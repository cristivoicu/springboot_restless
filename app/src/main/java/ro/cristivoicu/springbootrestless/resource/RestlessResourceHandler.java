package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.Version;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.patch.PatchDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.SoftDeletable;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbed;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.filter.FilterOperator;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.models.DeleteModel;
import ro.cristivoicu.springbootrestless.models.PageableResponse;
import ro.cristivoicu.springbootrestless.models.PatchModel;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Runtime-registered replacement for the four hand-subclassed {@code *Controller} classes.
 * <p>
 * Composes (does not replace) the entity's existing {@code CreateDataSource}/{@code ReadDataSource}/
 * {@code UpdateDataSource}/{@code DeleteDataSource} plus its {@link Mapper}s and {@link Specification}
 * builder — those stay hand-written and type-checked per aggregate, per the DDD boundary. What's new
 * here is only the HTTP wiring: one concrete, final handler method per route, shared (same {@link Method}
 * object) across every resource instance, dispatched by ordinary polymorphism once
 * {@code RestlessRegistrar} registers {@code (RequestMappingInfo, thisResourceBean, thatMethod)}.
 * <p>
 * Because DTO/id types aren't threaded through this class's own generics (deliberately — see the
 * implementation plan), request bodies/params are parsed against {@link ResourceMetadata}'s resolved
 * {@link Class} tokens at runtime, reusing Spring's own body/bind/validate infrastructure rather than
 * hand-rolling it.
 * <p>
 * <b>What's defaulted vs. what stays hand-written.</b> Create/Update/Delete have ready-made
 * reflection-based implementations ({@code datasource.defaults.Default*DataSource}, wired via
 * {@link #getCreateDataSource}/{@link #getUpdateDataSource}/{@link #getDeleteDataSource}), and
 * {@link #getSpecification} defaults to an equality filter on whichever {@code SearchDto} fields
 * are populated — both overridable per entity when the default isn't enough. {@link
 * #getCustomReadActions} adds named read actions beyond that default for logic a plain equality
 * filter can't express. {@link Mapper} deliberately has <em>no</em> such default — see its
 * javadoc — response shaping is the one boundary this framework always keeps hand-written, since
 * it's the surface fine-grained authorization has to reason about.
 * <p>
 * <b>Authorization.</b> {@link #getAuthorizationGuard} is the hook all of the above funnels
 * into: the actual point of defaulting CUD/read-filtering away is to make room for per-action
 * authorization to be the thing an entity author actually writes. See {@code
 * ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard}'s javadoc for its three
 * check points (coarse pre-check, row-level scope, per-instance access) and exactly where each
 * fires in the handler methods below.
 */
public abstract class RestlessResourceHandler<E, K> {

    /**
     * The full, default set {@link #getEnabledOperations} returns unless overridden - every
     * fixed route this framework registers, at the granularity {@code RestlessRegistrar.ROUTES}
     * groups them into (one {@link AuthorizationGuard.Action} per group of routes that share it -
     * see {@link #getEnabledOperations}'s own javadoc).
     */
    public static final Set<AuthorizationGuard.Action> ALL_OPERATIONS = Set.of(
            AuthorizationGuard.Action.CREATE, AuthorizationGuard.Action.READ_ONE,
            AuthorizationGuard.Action.READ_LIST, AuthorizationGuard.Action.READ_PAGE,
            AuthorizationGuard.Action.READ_PAGE_OVERVIEW, AuthorizationGuard.Action.READ_PAGE_SELECT,
            AuthorizationGuard.Action.UPDATE, AuthorizationGuard.Action.DELETE_ONE,
            AuthorizationGuard.Action.DELETE_ALL);

    private static final Method VALIDATION_TARGET_METHOD;

    static {
        try {
            VALIDATION_TARGET_METHOD = RestlessResourceHandler.class.getDeclaredMethod("validationTarget", Object.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Never invoked - exists purely so MethodArgumentNotValidException has a MethodParameter
    // to describe "the request body" with, matching what @Valid @RequestBody produces normally.
    @SuppressWarnings("unused")
    private void validationTarget(Object body) {
    }

    private ResourceMetadata metadata;
    private ObjectMapper objectMapper;
    private ConversionService conversionService;
    private Validator validator;
    private Constructor<?> searchDtoConstructor;
    private Map<String, Constructor<?>> customActionSearchDtoConstructors;
    private Class<?> patchModelType;
    private RestlessEmbedResolver embedResolver = RestlessEmbedResolver.NONE;
    private PlatformTransactionManager transactionManager;
    private RestlessAuthorizationMetrics metrics = RestlessAuthorizationMetrics.NONE;
    private int maxListSize = DEFAULT_MAX_LIST_SIZE;

    /**
     * {@link #findList}'s default hard cap on how many rows a single unpaginated {@code
     * GET .../list} request can return - unlike {@code findPage}/{@code findPageOverview}/
     * {@code findPageSelect}, {@code /list} has no client-supplied page size to bound it at all,
     * so with no cap a large table's entire contents (times however many concurrent requests hit
     * it) is exactly one route away from an {@code OutOfMemoryError}. Overridable per app via
     * {@code restless.list.max-size} (see {@code RestlessProperties}); 10,000 is generous enough
     * that no existing fixture/example dataset in this reactor ever comes close to it. A response
     * that hit the cap carries {@code X-Restless-List-Truncated: true} so a caller can tell
     * "that's genuinely everything" apart from "there's more - use {@code /page} instead".
     */
    public static final int DEFAULT_MAX_LIST_SIZE = 10_000;

    /**
     * Wires this resource's infra collaborators. Called once by whichever registrar discovered
     * this bean (a hardcoded call in Stage 1, {@code RestlessRegistrar} from Stage 2 on) — kept
     * separate from the constructor so the entity author's subclass stays free of infra plumbing.
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator) {
        init(metadata, objectMapper, conversionService, validator, RestlessEmbedResolver.NONE, null);
    }

    /**
     * Same as the four-arg {@link #init}, plus the {@link RestlessEmbedResolver} {@code
     * findOne()} uses to resolve {@code expand=} - a separate overload (not a fifth required
     * parameter on the original) purely so every pre-existing caller/test that constructs a
     * {@code RestlessResourceHandler} by hand keeps compiling unchanged, defaulted to {@link
     * RestlessEmbedResolver#NONE}. Only {@code RestlessRegistrar} calls this overload, with the
     * real, Spring-wired resolver bean.
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator,
                            RestlessEmbedResolver embedResolver) {
        init(metadata, objectMapper, conversionService, validator, embedResolver, null);
    }

    /**
     * Same as the five-arg {@link #init}, plus the {@link PlatformTransactionManager} {@link
     * #createBulk}/{@link #updateBulk}/{@link #deleteAll} wrap their actual database write in -
     * <b>not</b> {@code @Transactional}: every handler method here is deliberately {@code final}
     * (one shared {@link Method} object dispatched reflectively across every resource instance,
     * see this class's own javadoc), and Spring's proxy-based {@code @Transactional} support
     * cannot intercept a final method at all - CGLIB can't override it, so the annotation would
     * silently do nothing while still triggering a (useless) proxy and its own startup warnings
     * for every other final method here. {@code null} (the four/five-arg overloads' default) runs
     * a bulk write with no transaction boundary at all - today's original behavior, not a broken
     * one: every {@code Default*DataSource}/hand-written {@code *DataSource} still worked before
     * this existed, just without the atomicity guarantee a genuinely large batch benefits from.
     * Only {@code RestlessRegistrar} calls this overload, with the real
     * {@code JpaTransactionManager} Spring Data JPA already auto-configures.
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator,
                            RestlessEmbedResolver embedResolver, PlatformTransactionManager transactionManager) {
        init(metadata, objectMapper, conversionService, validator, embedResolver, transactionManager, RestlessAuthorizationMetrics.NONE);
    }

    /**
     * Same as the six-arg {@link #init}, plus the {@link RestlessAuthorizationMetrics} {@link
     * #checkPreCheck}/{@link #checkCanAccess} record an authorization-denial count to - a separate
     * overload for the same reason the five/six-arg ones are, defaulted to {@link
     * RestlessAuthorizationMetrics#NONE} (a no-op) for every pre-existing caller. Only {@code
     * RestlessRegistrar} calls this overload, with the real conditionally-registered bean (or its
     * own {@code NONE} fallback when Micrometer/Actuator aren't on the consumer's classpath).
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator,
                            RestlessEmbedResolver embedResolver, PlatformTransactionManager transactionManager,
                            RestlessAuthorizationMetrics metrics) {
        init(metadata, objectMapper, conversionService, validator, embedResolver, transactionManager, metrics, DEFAULT_MAX_LIST_SIZE);
    }

    /**
     * Same as the seven-arg {@link #init}, plus {@link #maxListSize} - a separate overload for
     * the same reason the others are: only {@code RestlessRegistrar} calls this one, with the
     * real value bound from {@code restless.list.max-size} (see {@code RestlessProperties}),
     * every pre-existing caller keeps getting {@link #DEFAULT_MAX_LIST_SIZE} unchanged.
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator,
                            RestlessEmbedResolver embedResolver, PlatformTransactionManager transactionManager,
                            RestlessAuthorizationMetrics metrics, int maxListSize) {
        this.metadata = metadata;
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.validator = validator;
        this.embedResolver = embedResolver;
        this.transactionManager = transactionManager;
        this.metrics = metrics == null ? RestlessAuthorizationMetrics.NONE : metrics;
        this.maxListSize = maxListSize > 0 ? maxListSize : DEFAULT_MAX_LIST_SIZE;
        this.searchDtoConstructor = resolveNoArgConstructor(metadata.searchDtoType(), "search DTO");

        this.customActionSearchDtoConstructors = new HashMap<>();
        for (Map.Entry<String, ReadAction<E, ?>> entry : getCustomReadActions().entrySet()) {
            Class<?> searchDtoType = entry.getValue().getSearchDtoType();
            customActionSearchDtoConstructors.put(entry.getKey(),
                    resolveNoArgConstructor(searchDtoType, "custom read action '" + entry.getKey() + "'"));
        }

        getPatchDataSource().ifPresent(patchDataSource -> this.patchModelType = resolveDtoType(patchDataSource, PatchDataSource.class));
    }

    private static Constructor<?> resolveNoArgConstructor(Class<?> type, String describedAs) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type + " (" + describedAs + ") needs a no-arg constructor to be bound from query parameters", e);
        }
    }

    public final ResourceMetadata getMetadata() {
        return metadata;
    }

    /**
     * The {@code PatchModel} type {@link #getPatchDataSource} exchanges over HTTP - resolved once
     * in {@link #init}, {@code null} whenever {@link #getPatchDataSource} is {@link
     * java.util.Optional#empty()} (no {@code PATCH} route registered at all, see its own
     * javadoc). Public for the same reason {@link #getCustomReadActions}/{@link
     * #getPatchDataSource} already are: read from a different package - {@code
     * ro.cristivoicu.springbootrestless.openapi}'s default document generation, describing the
     * {@code PATCH} route's request body - not just {@code RestlessRegistrar}.
     */
    public final Class<?> getPatchModelType() {
        return patchModelType;
    }

    /**
     * Resolves this resource's entity/id/DTO {@link Class} tokens via reflection off the
     * concrete subclass's own generics — {@code E}/{@code K} from this class's superclass
     * type arguments, each DTO type from the corresponding {@code *DataSource} subclass's
     * own generics. Called once by {@code RestlessRegistrar} at startup; kept here (not in
     * the registrar) because only code inside this class can call the protected
     * {@code getXDataSource()} accessors.
     */
    public final ResourceMetadata resolveMetadata(String basePath) {
        return resolveMetadata(basePath, "");
    }

    /**
     * Same as the one-arg {@link #resolveMetadata}, plus the resource's own {@code
     * @RestlessResource(version = ...)} value (empty string when unset) - carried into {@link
     * ResourceMetadata} purely for {@code RestlessOpenApiCustomizer} to surface in generated
     * documentation; every pre-existing caller of the one-arg overload keeps compiling unchanged,
     * defaulted to no version. Only {@code RestlessRegistrar} calls this overload, since it's the
     * only place that already has the annotation's {@code version()} in hand.
     */
    public final ResourceMetadata resolveMetadata(String basePath, String version) {
        Class<?>[] entityAndId = GenericTypeResolver.resolveTypeArguments(getClass(), RestlessResourceHandler.class);
        if (entityAndId == null) {
            throw new IllegalStateException(getClass() + " must extend RestlessResourceHandler<E, K> with concrete type arguments");
        }

        // Gated by getEnabledOperations(), not called unconditionally: a resource that disables
        // an operation (e.g. ProjectAssignmentRestlessResource excluding UPDATE) is entitled to
        // not override that operation's get*DataSource() accessor at all (see their now-non-
        // abstract, throwing defaults) - calling it anyway here, purely to resolve a DTO type
        // nothing will ever use, would crash every such resource at startup before a single
        // request arrives. null is safe: the only readers of these three fields are the
        // corresponding handler methods, and RestlessRegistrar never registers a route to reach
        // one whose operation is disabled.
        Set<AuthorizationGuard.Action> enabled = getEnabledOperations();
        Class<?> createModelType = enabled.contains(AuthorizationGuard.Action.CREATE)
                ? resolveDtoType(getCreateDataSource(), CreateDataSource.class) : null;
        Class<?> updateModelType = enabled.contains(AuthorizationGuard.Action.UPDATE)
                ? resolveDtoType(getUpdateDataSource(), UpdateDataSource.class) : null;
        Class<?> deleteModelType = (enabled.contains(AuthorizationGuard.Action.DELETE_ONE)
                || enabled.contains(AuthorizationGuard.Action.DELETE_ALL))
                ? resolveDtoType(getDeleteDataSource(), DeleteDataSource.class) : null;
        Class<?> searchDtoType = resolveDtoType(getReadDataSource(), ReadDataSource.class);

        return new ResourceMetadata(basePath, entityAndId[0], entityAndId[1],
                createModelType, updateModelType, deleteModelType, searchDtoType,
                resolveDtoTypeFromMapper(getEntityMapper()),
                resolveDtoTypeFromMapper(getOverviewMapper()),
                resolveDtoTypeFromMapper(getSelectMapper()),
                version == null ? "" : version);
    }

    /**
     * Resolves a {@code *DataSource}'s DTO type: via its own {@link TypedDataSource#getDtoType()}
     * if it's a directly-instantiated default data source (whose generics are erased at the
     * instance level), otherwise via {@link GenericTypeResolver} against its concrete subclass's
     * {@code extends} clause, exactly as before {@code TypedDataSource} existed.
     */
    private static Class<?> resolveDtoType(Object dataSource, Class<?> declaringClass) {
        if (dataSource instanceof TypedDataSource<?> typed) {
            return typed.getDtoType();
        }
        return GenericTypeResolver.resolveTypeArguments(dataSource.getClass(), declaringClass)[2];
    }

    /**
     * Same idiom as {@link #resolveDtoType}, off a {@code Mapper}'s own concrete class instead of
     * a {@code *DataSource} - {@code Mapper} has no {@link TypedDataSource} equivalent (nothing
     * generates a directly-instantiated default {@code Mapper} the way {@code Default*DataSource}
     * does), so this is always the {@link GenericTypeResolver} path. Best-effort: {@code null}, not
     * a thrown exception, for a {@code Mapper} whose generic signature isn't reifiable (an
     * anonymous class, a lambda) - see {@link ResourceMetadata#responseDtoType}'s own javadoc for
     * who actually reads this and how they're expected to handle {@code null}. Shared by {@link
     * #resolveMetadata} for all three of {@link #getEntityMapper}/{@link #getOverviewMapper}/
     * {@link #getSelectMapper} - each page-read variant's own response schema, rather than every
     * variant sharing {@code getEntityMapper()}'s.
     */
    private static Class<?> resolveDtoTypeFromMapper(Mapper<?, ?> mapper) {
        Class<?>[] mapperArgs = GenericTypeResolver.resolveTypeArguments(mapper.getClass(), Mapper.class);
        return mapperArgs == null ? null : mapperArgs[1];
    }

    /**
     * Unlike {@link #getReadDataSource} (still abstract - used internally for guard checks by
     * update/patch/delete even when no read route is exposed, see {@link #getEnabledOperations}),
     * Create/Update/Delete have no such internal use once their own route is disabled, so they're
     * no longer mandatory to implement at all: the default throws, and is only ever reached if a
     * resource enables the corresponding operation (see {@link #getEnabledOperations}) without
     * overriding the matching accessor - a real misconfiguration, not a routine case.
     */
    protected CreateDataSource<E, K, ?> getCreateDataSource() {
        throw new UnsupportedOperationException(getClass().getSimpleName()
                + " enables a CREATE operation but never overrides getCreateDataSource()");
    }

    protected abstract ReadDataSource<E, K, ?> getReadDataSource();

    protected UpdateDataSource<E, K, ?> getUpdateDataSource() {
        throw new UnsupportedOperationException(getClass().getSimpleName()
                + " enables an UPDATE operation but never overrides getUpdateDataSource()");
    }

    protected DeleteDataSource<E, K, ?> getDeleteDataSource() {
        throw new UnsupportedOperationException(getClass().getSimpleName()
                + " enables a DELETE_ONE/DELETE_ALL operation but never overrides getDeleteDataSource()");
    }

    protected abstract Mapper<E, ?> getEntityMapper();

    protected abstract Mapper<E, ?> getOverviewMapper();

    protected abstract Mapper<E, ?> getSelectMapper();

    /**
     * Named custom read actions beyond the default {@code findList}/{@code findPage*} routes,
     * each with its own {@link SearchDto} subtype and query logic — for anything the default
     * equality filter can't express. Empty by default. <b>Public, not protected</b>: {@code
     * RestlessRegistrar} lives in a different package and isn't a subclass, so it can't reach a
     * protected accessor — the same constraint that already applies to the {@code *DataSource}
     * accessors' underlying methods. Override (also {@code public}) to declare actions.
     */
    public Map<String, ReadAction<E, ?>> getCustomReadActions() {
        return Map.of();
    }

    /**
     * Named single-item views beyond the default {@code findOne}, each an alternate {@link
     * Mapper} for the same entity - the DDD-bounded-context case: {@code
     * GET {basePath}/{id}/billing} and {@code GET {basePath}/{id}/fulfillment} as two different
     * shapes of the same aggregate, keyed by name, each registered as its own route by {@code
     * RestlessRegistrar}. {@code getCustomReadActions()}'s counterpart for a single item rather
     * than a filtered collection - no {@link SearchDto}/{@link org.springframework.data.jpa.domain.Specification}
     * involved, since a view doesn't change *which* row is loaded, only *what shape* comes back.
     * Empty by default. <b>Public, not protected</b> - same reasoning as {@link
     * #getCustomReadActions}. Checked against {@link AuthorizationGuard.Action#NAMED_VIEW}, not
     * inherited from {@code READ_ONE} - a guard can grant the default view without silently
     * granting every named one too, the same "no silent inheritance between actions" reasoning
     * {@code PATCH} not inheriting {@code UPDATE} already documents; see {@link
     * AuthorizationGuard#canAccess(AuthorizationGuard.Action, String, HttpServletRequest, Object)}
     * for how a guard differentiates by view name.
     */
    public Map<String, Mapper<E, ?>> getNamedViews() {
        return Map.of();
    }

    /**
     * Named custom write actions beyond the default create/update/patch/delete — an
     * intent-carrying mutation ({@code POST {basePath}/{id}/actions/{name}}) rather than a
     * full-replace PUT, for anything an illegal-transition check or multi-step domain rule needs
     * a vocabulary for. Empty by default. <b>Public, not protected</b> — same reasoning as {@link
     * #getCustomReadActions}/{@link #getNamedViews}. Checked against {@link
     * AuthorizationGuard.Action#WRITE_ACTION}, not inherited from {@code UPDATE}/{@code PATCH} —
     * same "no silent inheritance between actions" reasoning as those two already document
     * relative to each other.
     */
    public Map<String, WriteAction<E, ?, ?>> getCustomWriteActions() {
        return Map.of();
    }

    /**
     * Partial-update ({@code PATCH}) support — entirely opt-in, unlike Create/Read/Update/Delete:
     * empty by default, meaning no {@code PATCH} route gets registered for this resource at all
     * (see {@code RestlessRegistrar}). Override (also {@code public}, same reasoning as {@link
     * #getCustomReadActions} - {@code RestlessRegistrar} needs to call this from a different
     * package) to add one, typically {@code Optional.of(new DefaultPatchDataSource<>(repository,
     * {Entity}PatchModel.class))} unless entity-specific partial-update logic is needed.
     */
    public Optional<PatchDataSource<E, K, ?>> getPatchDataSource() {
        return Optional.empty();
    }

    /**
     * Every fixed route ({@code create}/{@code createBulk}, {@code findOne}, {@code findList},
     * {@code findPage}/{@code findPageOverview}/{@code findPageSelect}, {@code update}/{@code
     * updateBulk}, {@code deleteById}, {@code deleteAll}) this resource exposes - keyed one
     * {@link AuthorizationGuard.Action} per <em>group</em> of routes sharing one, not one per
     * route: {@code createBulk} shares {@code CREATE} with {@code create}, {@code updateBulk}
     * shares {@code UPDATE} with {@code update}, so disabling {@code CREATE}/{@code UPDATE}
     * disables both the single-item and bulk route together. {@code PATCH} and named custom read
     * actions aren't part of this set at all - they're already independently opt-in via {@link
     * #getPatchDataSource}/{@link #getCustomReadActions}, so there's nothing here for them to
     * additionally gate.
     * <p>
     * Defaults to {@link #ALL_OPERATIONS} (today's behavior, unchanged) — override to expose only
     * a subset, e.g. a read-only resource:
     * <pre>{@code
     * public Set<AuthorizationGuard.Action> getEnabledOperations() {
     *     return Set.of(Action.READ_ONE, Action.READ_LIST, Action.READ_PAGE);
     * }
     * }</pre>
     * A disabled operation's own {@code get*DataSource()} accessor never has to be overridden
     * either (see {@link #getCreateDataSource}/{@link #getUpdateDataSource}/{@link
     * #getDeleteDataSource}'s now-non-abstract defaults) - a genuinely read-only resource needs
     * no {@code CreateDataSource}/{@code UpdateDataSource}/{@code DeleteDataSource} at all, not
     * even a never-reached one.
     */
    public Set<AuthorizationGuard.Action> getEnabledOperations() {
        return ALL_OPERATIONS;
    }

    /**
     * Per-action authorization hook — default-permissive, so authorization is opt-in per
     * resource rather than mandatory boilerplate. See {@link AuthorizationGuard}'s javadoc for
     * the three check points and when each fires.
     */
    protected AuthorizationGuard<E> getAuthorizationGuard() {
        return AuthorizationGuard.allowAll();
    }

    /**
     * Default filter: an equality predicate for every non-null, non-blank field declared
     * directly on the {@code SearchDto} subclass (its {@code getDeclaredFields()} already
     * excludes {@link ro.cristivoicu.springbootrestless.models.AbstractSearchDto}'s inherited
     * paging fields), ANDed together - unless the field's name ends with a recognized {@link
     * FilterOperator} suffix ({@code ageGte}, {@code nameLike}, {@code statusIn}, ...), in which
     * case that operator applies instead of equality. See {@code docs/design/filter-dsl.md} for
     * the full design (why a suffix convention rather than a query-language string, and why the
     * wire format is camelCase - {@code ?ageGte=30} - not snake_case). Override for anything
     * beyond these seven operators (joins, cross-field logic, boolean OR, ...) - or use {@link
     * ro.cristivoicu.springbootrestless.filter.RestlessSpecifications} to write that override
     * more tersely.
     * <p>
     * Primitive fields (e.g. {@code boolean}) are skipped entirely, not just when zero-valued:
     * a primitive can never represent "the client didn't send this filter" (Java always defaults
     * it, e.g. {@code false}), so treating an unset primitive field as an explicit filter would
     * silently exclude every non-default row from unfiltered searches. Use a boxed type
     * ({@code Boolean}) for an optional equality filter instead. An empty {@code Collection} (an
     * {@code In}-suffixed field nothing was bound to) gets the same "absent" treatment as a blank
     * string, for the same reason.
     * <p>
     * Two different failure modes for a misdeclared suffixed field, deliberately: a base property
     * that doesn't exist on {@code E} at all is silently ignored (a compile-time-fixed mistake in
     * the {@code SearchDto} author's own code, not client-controlled input - the same "absent
     * means unset" idiom the null/blank checks above already use); a base property that exists
     * but doesn't support the operator's type (e.g. {@code nameGte} where {@code name} is a
     * {@code String}) throws {@link IllegalStateException} - also the DTO author's own mistake,
     * wrong on every request rather than triggered by any particular one, so it doesn't belong in
     * the {@link ResponseStatusException}/400 vocabulary {@link #pageableOf}/{@link #convertId}/
     * {@link #readBody} use for genuinely client-triggered translation failures.
     */
    protected Specification<E> getSpecification(SearchDto searchDto) {
        Set<String> entityProperties = entityPropertyNames();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Field field : searchDto.getClass().getDeclaredFields()) {
                if (field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(searchDto);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Could not read " + field + " for default filtering", e);
                }
                if (value == null) {
                    continue;
                }
                if (value instanceof CharSequence text && !StringUtils.hasText(text.toString())) {
                    continue;
                }
                if (value instanceof Collection<?> collection && collection.isEmpty()) {
                    continue;
                }

                FilterOperator operator = FilterOperator.forFieldName(field.getName());
                if (operator == null) {
                    predicates.add(cb.equal(root.get(field.getName()), value));
                    continue;
                }
                String baseProperty = operator.basePropertyOf(field.getName());
                if (!entityProperties.contains(baseProperty)) {
                    continue;
                }
                if (operator == FilterOperator.IN) {
                    if (!(value instanceof Collection)) {
                        throw new IllegalStateException(field + " uses the 'In' suffix but isn't a Collection");
                    }
                    predicates.add(root.<Object>get(baseProperty).in((Collection<?>) value));
                    continue;
                }
                Class<?> entityFieldType = entityFieldType(baseProperty);
                if (!operator.supports(entityFieldType)) {
                    throw new IllegalStateException(field + " uses '" + operator + "' but "
                            + metadata.entityType().getSimpleName() + "." + baseProperty
                            + " (" + entityFieldType + ") doesn't support it");
                }
                predicates.add(operator.predicate(cb, root.get(baseProperty), value));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * {@code baseProperty} is already confirmed present somewhere in {@code
     * metadata.entityType()}'s own class hierarchy by the caller (via {@link
     * #entityPropertyNames}) - walks the same hierarchy here to actually find and read its type,
     * since a plain {@code getDeclaredField} on the concrete class alone would miss one declared
     * on a shared {@code @MappedSuperclass}. The final {@code throw} is unreachable in practice.
     */
    private Class<?> entityFieldType(String baseProperty) {
        for (Class<?> type = metadata.entityType(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(baseProperty).getType();
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new IllegalStateException("No field '" + baseProperty + "' found on " + metadata.entityType());
    }

    // ---- shared handler methods: one Method object per route, inherited by every subclass ----

    public final ResponseEntity<?> create(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.CREATE, null, request);
        Object body = readBody(request, metadata.createModelType());
        validate(body);
        // Raw-type escape hatch: C's bound (CreateModel) can't be named here since the actual
        // type is only known at runtime via metadata.createModelType(). Safe because `body` was
        // just deserialized as exactly that class.
        @SuppressWarnings({"unchecked", "rawtypes"})
        CreateDataSource rawDataSource = getCreateDataSource();
        @SuppressWarnings("unchecked")
        E created = (E) rawDataSource.create((CreateModel) body);
        // 201 + Location, not 200: RFC 9110 §15.3.2 - a successful POST that creates a resource
        // should report 201 and point at where the new resource can be fetched. idOf(created)
        // can come back null for an entity with no @Id field reachable via reflection walk-up
        // (shouldn't happen for a real JPA entity, but a hand-rolled test double might skip it) -
        // falls back to plain 200 with no Location rather than building a broken URI in that case.
        Object id = idOf(created);
        if (id != null) {
            java.net.URI location = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                    .fromRequest(request).path("/{id}").buildAndExpand(id).toUri();
            return ResponseEntity.created(location).body(getEntityMapper().map(created));
        }
        return ResponseEntity.ok(getEntityMapper().map(created));
    }

    /**
     * Reflection-based {@code @Id} field read, shared by {@link #create}'s {@code Location}
     * header - walks the class hierarchy (not just {@code getDeclaredFields()} on the runtime
     * class alone), since {@code @Id} commonly lives on a shared {@code @MappedSuperclass} rather
     * than on the concrete entity itself. Same idiom {@code DefaultReadDataSource#idOf} already
     * uses for its own cross-check against a {@code Specification}-scoped fetch; duplicated
     * (not shared) rather than introducing a coupling between this class and one specific
     * default data source implementation for a two-line reflection walk.
     */
    private static Object idOf(Object entity) {
        if (entity == null) {
            return null;
        }
        for (Class<?> type = entity.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    field.setAccessible(true);
                    try {
                        return field.get(entity);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Could not read @Id field of " + entity.getClass(), e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Bulk create - {@code POST {basePath}/bulk}, body a JSON array of {@code CreateModel}s.
     * {@link #checkPreCheck} is one coarse "can this principal create at all" call, same as
     * single {@link #create} - there's no per-item {@code canAccess} the way bulk update/delete
     * have, since none of these rows exist yet for a per-instance check to run against. Every
     * item is validated before any of them are created (fail-fast, same spirit as {@link
     * #deleteAll}'s per-id guard check running entirely before the first delete).
     * <p>
     * {@link #inTransaction}: {@link CreateDataSource#createAll}'s default is a plain loop of
     * {@link CreateDataSource#create} calls, each independently committing via {@code
     * JpaRepository.save()} unless something wraps the whole request in one transaction - without
     * this, item 9,999 of a 10,000-row batch failing (a duplicate, an invalid reference, ...)
     * would leave the first 9,998 committed instead of rolling back. A bulk write should be
     * all-or-nothing at the database level, not just "validated up front" - the two aren't the
     * same guarantee.
     */
    public final ResponseEntity<?> createBulk(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.CREATE, null, request);
        List<Object> bodies = readBodyList(request, metadata.createModelType());
        for (Object body : bodies) {
            validate(body);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        CreateDataSource rawDataSource = getCreateDataSource();
        List<E> created = inTransaction(() -> {
            @SuppressWarnings("unchecked")
            List<E> result = rawDataSource.createAll(bodies);
            return result;
        });
        return ResponseEntity.ok(getEntityMapper().map(created));
    }

    public final ResponseEntity<?> findOne(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.READ_ONE, null, request);
        K id = extractId(request);
        // inReadOnlyTransaction: getEntityMapper().map(...) and embedResolver.resolve(...) both
        // run inside it - see that method's own javadoc for why a lazy association needs this.
        return inReadOnlyTransaction(() -> {
            E found = getReadDataSource().findOne(id);
            if (found == null) {
                return ResponseEntity.notFound().build();
            }
            checkCanAccess(AuthorizationGuard.Action.READ_ONE, request, found);
            Object dto = getEntityMapper().map(found);
            // Opt-in (?expand=name,...), see RestlessEmbed - a no-op for a request that doesn't
            // ask for anything, and for a resource whose DTO declares no @RestlessEmbed field.
            embedResolver.resolve(dto, found, request);
            return ResponseEntity.ok(dto);
        });
    }

    public final ResponseEntity<List<?>> findList(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.READ_LIST, null, request);
        SearchDto searchDto = bindSearchDto(searchDtoConstructor, request);
        Specification<E> spec = withScope(excludeSoftDeleted(getSpecification(searchDto)), AuthorizationGuard.Action.READ_LIST, null, request);
        // Capped, not a plain findAll(spec): see maxListSize's own javadoc for why an unpaginated
        // route needs a hard limit at all. One extra row requested (maxListSize + 1) so "hit the
        // cap" can be told apart from "exactly maxListSize rows existed" without a second COUNT
        // query - the (maxListSize + 1)-th row itself is trimmed back off before mapping.
        Sort sort = pageableOf(searchDto).getSort();
        // inReadOnlyTransaction: getOverviewMapper().map(...) runs inside it too - see that
        // method's own javadoc for why a lazy association needs this.
        return inReadOnlyTransaction(() -> {
            List<E> data = getReadDataSource().findAll(spec, org.springframework.data.domain.PageRequest.of(0, maxListSize + 1, sort)).getContent();
            boolean truncated = data.size() > maxListSize;
            List<E> page = truncated ? data.subList(0, maxListSize) : data;
            ResponseEntity.BodyBuilder response = truncated
                    ? ResponseEntity.ok().header("X-Restless-List-Truncated", "true")
                    : ResponseEntity.ok();
            return response.body(getOverviewMapper().map(page));
        });
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPage(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.READ_PAGE, null, request);
        return ResponseEntity.ok(paginate(request, getEntityMapper(), AuthorizationGuard.Action.READ_PAGE));
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPageOverview(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.READ_PAGE_OVERVIEW, null, request);
        return ResponseEntity.ok(paginate(request, getOverviewMapper(), AuthorizationGuard.Action.READ_PAGE_OVERVIEW));
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPageSelect(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.READ_PAGE_SELECT, null, request);
        return ResponseEntity.ok(paginate(request, getSelectMapper(), AuthorizationGuard.Action.READ_PAGE_SELECT));
    }

    /**
     * Shared entry point for every named {@link ReadAction}: one {@link Method} object,
     * registered once per declared action name pointing back at this same method (see
     * {@code RestlessRegistrar}), dispatching by recovering which literal path matched via
     * {@link HandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE} — the same "read a {@code
     * HandlerMapping.*_ATTRIBUTE} off the request" convention {@link #extractId} already uses.
     */
    public final ResponseEntity<PageableResponse<List<?>>> customRead(HttpServletRequest request) throws Exception {
        String actionName = resolveActionName(request);
        checkPreCheck(AuthorizationGuard.Action.CUSTOM_READ, actionName, request);
        ReadAction<E, ?> action = getCustomReadActions().get(actionName);
        if (action == null) {
            return ResponseEntity.notFound().build();
        }

        SearchDto searchDto = bindSearchDto(customActionSearchDtoConstructors.get(actionName), request);
        // Raw-type escape hatch, same reasoning as create()/update(): R's bound (SearchDto) can't
        // be named here since it's only known at runtime via the action's own getSearchDtoType().
        @SuppressWarnings({"unchecked", "rawtypes"})
        ReadAction rawAction = action;
        @SuppressWarnings("unchecked")
        Specification<E> spec = (Specification<E>) rawAction.buildSpecification(searchDto);
        spec = withScope(excludeSoftDeleted(spec), AuthorizationGuard.Action.CUSTOM_READ, actionName, request);

        return ResponseEntity.ok(paginate(getOverviewMapper(), spec, pageableOf(searchDto)));
    }

    /**
     * Shared entry point for every named view in {@link #getNamedViews()} - same one-{@link
     * Method}-per-name dispatch idiom as {@link #customRead}, reusing {@link #resolveActionName}
     * unchanged (it already just recovers "whatever the last literal path segment was," nothing
     * custom-read-specific about it despite the name). Unlike {@link #customRead}, this loads by
     * id ({@code {basePath}/{id}/{viewName}}) rather than filtering a collection, so it 404s the
     * same way {@link #findOne} does for a missing row, and supports {@code ?expand=} the same way
     * too.
     */
    public final ResponseEntity<?> namedView(HttpServletRequest request) throws Exception {
        String viewName = resolveActionName(request);
        checkPreCheck(AuthorizationGuard.Action.NAMED_VIEW, viewName, request);
        K id = extractId(request);
        // inReadOnlyTransaction: mapper.map(...)/embedResolver.resolve(...) run inside it too -
        // see that method's own javadoc for why a lazy association needs this.
        return inReadOnlyTransaction(() -> {
            E found = getReadDataSource().findOne(id);
            if (found == null) {
                return ResponseEntity.notFound().build();
            }
            checkCanAccess(AuthorizationGuard.Action.NAMED_VIEW, viewName, request, found);
            Mapper<E, ?> mapper = getNamedViews().get(viewName);
            if (mapper == null) {
                return ResponseEntity.notFound().build();
            }
            Object dto = mapper.map(found);
            embedResolver.resolve(dto, found, request);
            return ResponseEntity.ok(dto);
        });
    }

    /**
     * Shared entry point for every named {@link WriteAction} — same one-{@link Method}-per-name
     * dispatch idiom as {@link #customRead}/{@link #namedView}. Unlike {@link #customRead} (a
     * filtered collection, no id) and like {@link #namedView} (loads by id first), this loads the
     * target entity, guard-checks it, then hands it to the action — but unlike {@link #namedView}
     * (read-only, {@link #inReadOnlyTransaction}), the load + guard-check + {@link
     * WriteAction#execute} call all run inside one real {@link #inTransaction} boundary, the same
     * atomicity guarantee {@link #createBulk}/{@link #updateBulk}/{@link #deleteAll} get and
     * single {@link #update}/{@link #patch} don't need (a single {@code *DataSource} save is
     * already atomic on its own) — a write action's own {@link WriteAction#execute} is explicitly
     * allowed to be multi-step domain logic, so it gets the same explicit boundary a bulk write
     * does.
     * <p>
     * The unknown-action-name check runs before the request body is even read (not just before
     * the entity is loaded) — deliberately: {@code action.getRequestType()} is what tells {@link
     * #readBody} what to deserialize into, so there is no type to parse against until the action
     * itself is known. Body reading/validation then happens before the transaction opens (same
     * order {@link #deleteAll} already uses: validate everything first, only the actual DB work
     * runs inside the transaction) — not after the load+guard, unlike {@link #update}/{@link
     * #patch}, since those two have no transaction boundary to be forced to open before or after
     * that check at all.
     * <p>
     * 200, not 201, on success: a write action mutates an existing resource in place (closer to
     * {@link #update} semantically), even though the HTTP verb is POST — {@link #create}'s own
     * 201/{@code Location} reasoning is specifically about a <em>new</em> resource, which doesn't
     * apply here. Illegal-transition prevention (this mechanism's actual reason to exist) needs no
     * new framework machinery at all: an implementation of {@link WriteAction#execute} throws a
     * {@link ResponseStatusException} directly (any status), which propagates out through {@link
     * #inTransaction} to {@code RestlessExceptionHandler}'s existing generic handling, rolling
     * back cleanly — the same path a guard denial inside {@link #deleteAll}/{@link #updateBulk}
     * already takes today.
     */
    public final ResponseEntity<?> writeAction(HttpServletRequest request) throws Exception {
        String actionName = resolveActionName(request);
        checkPreCheck(AuthorizationGuard.Action.WRITE_ACTION, actionName, request);
        WriteAction<E, ?, ?> action = getCustomWriteActions().get(actionName);
        if (action == null) {
            return ResponseEntity.notFound().build();
        }

        K id = extractId(request);
        // Typed as WriteActionRequest, not Object: rawAction.execute(...) below is a raw-type
        // call, and raw-type erasure keeps a bounded type parameter's own upper bound in the
        // erased signature (Req extends WriteActionRequest erases to WriteActionRequest, not
        // Object) - same reason customRead()'s own searchDto local is typed SearchDto, not Object.
        WriteActionRequest body = (WriteActionRequest) readBody(request, action.getRequestType());
        validate(body);

        // Raw-type escape hatch, same reasoning as customRead()/create()/update(): Req/Resp can't
        // be named here since they're only known at runtime via the action's own getRequestType().
        @SuppressWarnings({"unchecked", "rawtypes"})
        WriteAction rawAction = action;

        return inTransaction(() -> {
            E found = getReadDataSource().findOne(id);
            if (found == null) {
                return ResponseEntity.notFound().build();
            }
            checkCanAccess(AuthorizationGuard.Action.WRITE_ACTION, actionName, request, found);
            @SuppressWarnings("unchecked")
            Object response = rawAction.execute(found, body);
            return ResponseEntity.ok(response);
        });
    }

    public final ResponseEntity<?> update(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.UPDATE, null, request);
        K id = extractId(request);
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        // Loaded purely for the guard check and/or the If-Match precondition (see checkIfMatch) -
        // UpdateDataSource.update() loads/mutates/saves as one atomic unit and never hands the
        // entity back to us beforehand. Skipped entirely (not just short-circuited on a denial)
        // when neither applies, so a resource with no guard and no @Version field doesn't pay
        // for an extra SELECT on every write.
        if (hasGuard() || ifMatch != null) {
            E existing = getReadDataSource().findOne(id);
            if (existing != null) {
                if (hasGuard()) {
                    checkCanAccess(AuthorizationGuard.Action.UPDATE, request, existing);
                }
                checkIfMatch(ifMatch, existing);
            }
        }
        Object body = readBody(request, metadata.updateModelType());
        validate(body);
        @SuppressWarnings({"unchecked", "rawtypes"})
        UpdateDataSource rawDataSource = getUpdateDataSource();
        @SuppressWarnings("unchecked")
        E updated = (E) rawDataSource.update(id, (UpdateModel) body);
        return ResponseEntity.ok(getEntityMapper().map(updated));
    }

    /**
     * Only ever dispatched to at all when {@link #getPatchDataSource()} is non-empty - {@code
     * RestlessRegistrar} doesn't register a {@code PATCH} route otherwise (see its own reasoning
     * for why, mirroring {@link #getCustomReadActions()}'s per-action registration). {@code
     * Action.PATCH} is checked, not {@code Action.UPDATE} - deliberately not inherited, so a
     * policy granting full-replace access doesn't silently also grant partial-update access
     * without an explicit decision (see {@code AuthorizationGuard.Action}'s javadoc-equivalent
     * reasoning already applied to every other action here).
     */
    public final ResponseEntity<?> patch(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.PATCH, null, request);
        K id = extractId(request);
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        if (hasGuard() || ifMatch != null) {
            E existing = getReadDataSource().findOne(id);
            if (existing != null) {
                if (hasGuard()) {
                    checkCanAccess(AuthorizationGuard.Action.PATCH, request, existing);
                }
                checkIfMatch(ifMatch, existing);
            }
        }
        Object body = readBody(request, patchModelType);
        validate(body);
        @SuppressWarnings({"unchecked", "rawtypes"})
        PatchDataSource rawDataSource = getPatchDataSource().orElseThrow(
                () -> new IllegalStateException("PATCH route registered but getPatchDataSource() is now empty"));
        @SuppressWarnings("unchecked")
        E patched = (E) rawDataSource.patch(id, (PatchModel) body);
        return ResponseEntity.ok(getEntityMapper().map(patched));
    }

    /**
     * Bulk update - {@code PUT {basePath}/bulk}, body a JSON object keyed by id ({@code
     * {"1": {...update fields...}, "2": {...}}}), each value the same shape a single {@code PUT
     * {basePath}/{id}} takes. Every target is loaded and guard-checked before any of them are
     * updated - fail-fast, same reasoning as {@link #deleteAll}'s per-id check running entirely
     * before the first delete: a bulk write should never partially apply because item #7 of 10
     * turned out to be denied. {@link #inTransaction} for the same reason {@link #createBulk}
     * needs it: the guard check happening up front doesn't protect against a later item's own
     * {@code UpdateDataSource#update} call failing partway through a large batch - the guard-check
     * loop and the actual write both run inside the same transaction here (unlike {@code
     * createBulk}, which has no pre-write reads to include), so a row can't change between being
     * checked and being written either.
     */
    public final ResponseEntity<?> updateBulk(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.UPDATE, null, request);
        Map<String, Object> rawBodies = readBodyMap(request, metadata.updateModelType());

        Map<K, Object> byId = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawBodies.entrySet()) {
            validate(entry.getValue());
            byId.put(convertId(entry.getKey()), entry.getValue());
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        UpdateDataSource rawDataSource = getUpdateDataSource();
        List<E> updated = inTransaction(() -> {
            if (hasGuard()) {
                for (K id : byId.keySet()) {
                    E existing = getReadDataSource().findOne(id);
                    if (existing != null) {
                        checkCanAccess(AuthorizationGuard.Action.UPDATE, request, existing);
                    }
                }
            }
            @SuppressWarnings("unchecked")
            List<E> result = rawDataSource.updateAll(byId);
            return result;
        });
        return ResponseEntity.ok(getEntityMapper().map(updated));
    }

    public final ResponseEntity<?> deleteById(HttpServletRequest request) {
        checkPreCheck(AuthorizationGuard.Action.DELETE_ONE, null, request);
        K id = extractId(request);
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        // Same reasoning as update(): loaded purely for the guard check and/or If-Match, skipped
        // entirely when neither applies; not found falls through unchanged to DeleteDataSource's
        // own (today: silent) not-found behavior.
        if (hasGuard() || ifMatch != null) {
            E existing = getReadDataSource().findOne(id);
            if (existing != null) {
                if (hasGuard()) {
                    checkCanAccess(AuthorizationGuard.Action.DELETE_ONE, request, existing);
                }
                checkIfMatch(ifMatch, existing);
            }
        }
        getDeleteDataSource().deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public final ResponseEntity<?> deleteAll(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.DELETE_ALL, null, request);
        Object body = readBody(request, metadata.deleteModelType());
        DeleteModel deleteModel = (DeleteModel) body;
        @SuppressWarnings({"unchecked", "rawtypes"})
        DeleteDataSource rawDataSource = getDeleteDataSource();
        // Fail-fast, before deleting anything: check every targeted entity up front so a bulk
        // delete never partially completes before hitting a denied id. Skipped entirely (the
        // whole loop, not just the check) when no guard is configured. Both the check loop and
        // the actual delete run inside the same transaction (see #inTransaction) - a row can't
        // change between being checked and being deleted either.
        this.<Void>inTransaction(() -> {
            if (hasGuard()) {
                for (String rawId : deleteModel.getIds()) {
                    E existing = getReadDataSource().findOne(convertId(rawId));
                    if (existing != null) {
                        checkCanAccess(AuthorizationGuard.Action.DELETE_ALL, request, existing);
                    }
                }
            }
            rawDataSource.deleteAll(deleteModel);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    // ---- shared per-request parsing, reusing Spring's own infra rather than hand-rolling it ----

    private PageableResponse<List<?>> paginate(HttpServletRequest request, Mapper<E, ?> mapper, AuthorizationGuard.Action action) throws Exception {
        SearchDto searchDto = bindSearchDto(searchDtoConstructor, request);
        Specification<E> spec = withScope(excludeSoftDeleted(getSpecification(searchDto)), action, null, request);
        return paginate(mapper, spec, pageableOf(searchDto));
    }

    /**
     * Shared by {@link #findPage}/{@link #findPageOverview}/{@link #findPageSelect} (via the
     * request-binding overload above, using {@link #getSpecification}) and {@link #customRead}
     * (using a {@link ReadAction}'s own specification) — pagination/response-shaping logic is
     * identical either way, only the filter source and mapper differ.
     */
    private PageableResponse<List<?>> paginate(Mapper<E, ?> mapper, Specification<E> spec, Pageable pageable) throws Exception {
        // inReadOnlyTransaction: mapper.map(...) runs inside it too - see that method's own
        // javadoc for why a lazy association needs this. Shared by findPage/findPageOverview/
        // findPageSelect/customRead (via the two callers below), so this one wrap covers all four.
        return inReadOnlyTransaction(() -> {
            Page<E> page = getReadDataSource().findAll(spec, pageable);

            PageableResponse<List<?>> response = new PageableResponse<>();
            response.setPageSize(page.getSize());
            response.setTotalPages(page.getTotalPages());
            response.setTotalElements(page.getTotalElements());
            response.setBody(mapper.map(page.getContent()));
            return response;
        });
    }

    /**
     * {@link SearchDto#getPageable()}, validated against this resource's own entity before a
     * single query ever runs: an unresolvable {@code sort} direction ({@code
     * AbstractSearchDto#getPageable()} throws {@link IllegalArgumentException} for one) or a
     * property that isn't an actual field on {@code E} both become a clean 400 here instead of
     * either an opaque 500 (the direction case) or Hibernate's own, much later and much less
     * clear failure once the query actually runs (the property case) - the same "translate to 400
     * by hand, at the boundary, since there's no normal argument-resolution pipeline to get this
     * for free from" idiom {@link #convertId}/{@link #readBody} already use.
     */
    private Pageable pageableOf(SearchDto searchDto) {
        Pageable pageable;
        try {
            pageable = searchDto.getPageable();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort: " + e.getMessage(), e);
        }

        Set<String> entityProperties = entityPropertyNames();
        for (Sort.Order order : pageable.getSort()) {
            if (!entityProperties.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown sort property '" + order.getProperty() + "' for " + metadata.entityType().getSimpleName());
            }
        }
        return pageable;
    }

    /**
     * Every field name declared anywhere in {@code metadata.entityType()}'s own class hierarchy
     * (up to, not including, {@code Object}) - not just {@code getDeclaredFields()} on the
     * concrete class alone, which would miss anything declared on a shared {@code
     * @MappedSuperclass} like {@link ro.cristivoicu.springbootrestless.models.AbstractAuditableEntity}
     * ({@code createdDate}/{@code lastModifiedDate} - see {@code Project}, this reactor's one
     * demo of it). Same class-hierarchy walk {@code DefaultReadDataSource#idOf}/{@code
     * RestlessResourceHandler#idOf} already use for locating an inherited {@code @Id} field.
     * Shared by {@link #pageableOf} (sort-property validation) and {@link #getSpecification}
     * (filter-DSL base-property validation) - one source of truth for "is this a real property on
     * this entity," recomputed per call rather than cached, since it's cheap reflection over a
     * handful of fields, not a hot path.
     */
    private Set<String> entityPropertyNames() {
        Set<String> names = new HashSet<>();
        for (Class<?> type = metadata.entityType(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private Object readBody(HttpServletRequest request, Class<?> type) throws java.io.IOException {
        // Bypasses HttpMessageConverter (there's no typed @RequestBody parameter to hang one off
        // of), so malformed JSON must be translated to 400 by hand - otherwise it surfaces as an
        // unhandled JacksonException (500) where the hand-written routes get
        // HttpMessageNotReadableException (400) for free from the framework.
        try {
            return objectMapper.readValue(request.getInputStream(), type);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body", e);
        }
    }

    /** {@link #readBody}, but a JSON array of {@code elementType} - backs bulk create. */
    @SuppressWarnings("unchecked")
    private List<Object> readBodyList(HttpServletRequest request, Class<?> elementType) throws java.io.IOException {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            return (List<Object>) objectMapper.readValue(request.getInputStream(), listType);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body", e);
        }
    }

    /**
     * {@link #readBody}, but a JSON object keyed by id, each value {@code elementType} - backs
     * bulk update. Keys stay {@code String} here (not yet converted to {@code K}) - callers
     * convert each one through {@link #convertId} individually, the same "translate to 400 by
     * hand" idiom that method already applies for a single {@code PUT}/{@code DELETE}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readBodyMap(HttpServletRequest request, Class<?> elementType) throws java.io.IOException {
        JavaType mapType = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, elementType);
        try {
            return (Map<String, Object>) objectMapper.readValue(request.getInputStream(), mapType);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body", e);
        }
    }

    private SearchDto bindSearchDto(Constructor<?> constructor, HttpServletRequest request) throws Exception {
        SearchDto searchDto = (SearchDto) constructor.newInstance();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(searchDto);
        binder.setConversionService(conversionService);
        binder.bind(request);
        return searchDto;
    }

    /**
     * Recovers which literal {@code {basePath}/actions/{name}} route matched, so the one shared
     * {@link #customRead} method can tell which {@link ReadAction} to run — actions are
     * registered as distinct literal paths (not a {@code {name}} template), so the name isn't
     * available as a URI template variable the way {@code id} is in {@link #extractId}.
     */
    private String resolveActionName(HttpServletRequest request) {
        String pathWithinMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        return pathWithinMapping.substring(pathWithinMapping.lastIndexOf('/') + 1);
    }

    @SuppressWarnings("unchecked")
    private K extractId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String rawId = ((Map<String, String>) attribute).get("id");
        return convertId(rawId);
    }

    @SuppressWarnings("unchecked")
    private K convertId(String rawId) {
        // Bypasses the normal @PathVariable argument resolver, so a malformed id (e.g. "abc" for
        // a Long) must be translated to 400 by hand here - otherwise it surfaces as an unhandled
        // ConversionException (500) where the hand-written routes get
        // MethodArgumentTypeMismatchException (400) for free from the framework. Shared by
        // extractId() (path variable) and deleteAll()'s per-id guard-check loop.
        try {
            return conversionService.convert(rawId, (Class<K>) metadata.idType());
        } catch (ConversionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to convert id '" + rawId + "' to " + metadata.idType().getSimpleName(), e);
        }
    }

    private void validate(Object target) throws MethodArgumentNotValidException {
        BindingResult errors = new BeanPropertyBindingResult(target, target.getClass().getSimpleName());
        validator.validate(target, errors);
        if (errors.hasErrors()) {
            throw new MethodArgumentNotValidException(new MethodParameter(VALIDATION_TARGET_METHOD, 0), errors);
        }
    }

    // ---- AuthorizationGuard wiring ----

    /**
     * Whether this resource opted into a real guard, vs. the default permissive
     * {@link AuthorizationGuard#allowAll()} singleton — reference-comparable specifically
     * because {@code allowAll()} always returns that same instance. Lets update()/deleteById()/
     * deleteAll() skip their extra {@code findOne} load entirely (not just short-circuit on a
     * denial) when nothing is actually going to deny anything.
     */
    private boolean hasGuard() {
        return getAuthorizationGuard() != AuthorizationGuard.allowAll();
    }

    /**
     * Public mirror of {@link #hasGuard()} - {@code RestlessRegistrar} (a different package,
     * {@code protected}/private members here aren't reachable from it) calls this at startup to
     * enforce {@code @RestlessResource#allowAll}'s fail-fast check: a resource with no real guard
     * configured and no explicit {@code allowAll = true} never gets its routes registered at all,
     * an {@link IllegalStateException} instead. Same reference-equality reasoning as {@link
     * #hasGuard()} — cheap, and correct precisely because {@link AuthorizationGuard#allowAll()}
     * always returns the same singleton.
     */
    public final boolean hasExplicitAuthorizationGuard() {
        return hasGuard();
    }

    /**
     * Opt-in optimistic-concurrency precondition for single-item {@link #update}/{@link
     * #patch}/{@link #deleteById}: a no-op whenever {@code ifMatch} is {@code null} (no header
     * sent) or {@code entity}'s type has no {@code @jakarta.persistence.Version} field at all (see
     * {@link #readVersion}) - fully backward compatible with every entity that predates this
     * feature. When both are present and disagree, this is the client racing a stale read against
     * a write that already landed - reported as 412, not the 409 a raw {@code
     * ObjectOptimisticLockingFailureException} from an actual concurrent {@code save()} maps to
     * (see {@code RestlessExceptionHandler}), since this check runs before any write is even
     * attempted.
     */
    private void checkIfMatch(String ifMatch, E entity) {
        if (ifMatch == null) {
            return;
        }
        String expected = stripEtagWrapper(ifMatch);
        readVersion(entity).ifPresent(actual -> {
            if (!expected.equals(actual)) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "If-Match '" + ifMatch + "' does not match current version '" + actual + "'");
            }
        });
    }

    /**
     * Reflectively finds {@code entity}'s {@code @jakarta.persistence.Version} field (if any) and
     * returns its current value as a string - the same "scan declared fields for an annotation"
     * idiom {@link #getSpecification}'s default equality filter already uses. {@link
     * Optional#empty()} for an entity type with no such field, which {@link #checkIfMatch} treats
     * as "this entity doesn't support optimistic locking, so an If-Match header on it can't be
     * honored" rather than an error.
     */
    private Optional<String> readVersion(E entity) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Version.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(entity);
                    return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Could not read @Version field " + field + " for If-Match support", e);
                }
            }
        }
        return Optional.empty();
    }

    /** Strips a leading weak-validator marker ({@code W/}) and surrounding quotes, so both a raw version number and a properly-quoted HTTP ETag are accepted as {@code If-Match}. */
    private static String stripEtagWrapper(String etag) {
        String value = etag.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void checkPreCheck(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request) {
        if (!getAuthorizationGuard().preCheck(action, customActionName, request)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "preCheck");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to perform " + action);
        }
    }

    private void checkCanAccess(AuthorizationGuard.Action action, HttpServletRequest request, E entity) {
        if (!getAuthorizationGuard().canAccess(action, request, entity)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccess");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this " + metadata.entityType().getSimpleName());
        }
    }

    /** Same as {@link #checkCanAccess(AuthorizationGuard.Action, HttpServletRequest, Object)}, threading a name through to the guard's own name-aware overload - see {@link #namedView}, the only caller. */
    private void checkCanAccess(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request, E entity) {
        if (!getAuthorizationGuard().canAccess(action, customActionName, request, entity)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccess");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this " + metadata.entityType().getSimpleName());
        }
    }

    private Specification<E> withScope(Specification<E> spec, AuthorizationGuard.Action action, String customActionName, HttpServletRequest request) {
        Specification<E> scope = getAuthorizationGuard().scope(action, customActionName, request);
        return scope == null ? spec : spec.and(scope);
    }

    /**
     * ANDs in a {@code deleted = false} predicate for entities implementing {@link
     * SoftDeletable} - a no-op for every other entity. Applied to every list/page/custom-read
     * filter (see the three {@code getSpecification(...)}/{@code buildSpecification(...)} call
     * sites above), the same {@code .and(...)} composition idiom {@link #withScope} already uses
     * for authorization scoping. Deliberately <b>not</b> applied to {@link #findOne}/{@link
     * #update}/{@link #patch} (all of which load by id via {@link #getReadDataSource()} directly,
     * bypassing {@code Specification} filtering entirely) - a soft-deleted row stays fetchable and
     * restorable by id on purpose, only excluded from listing/searching.
     */
    private Specification<E> excludeSoftDeleted(Specification<E> spec) {
        if (!SoftDeletable.class.isAssignableFrom(metadata.entityType())) {
            return spec;
        }
        return spec.and((root, query, cb) -> cb.equal(root.get("deleted"), false));
    }

    // ---- explicit transaction demarcation for the three bulk-write routes ----

    /**
     * Runs {@code work} inside a real transaction when {@link #init} was given a {@link
     * PlatformTransactionManager} (see the six-arg overload's javadoc for why this - not {@code
     * @Transactional} - is how {@link #createBulk}/{@link #updateBulk}/{@link #deleteAll} get
     * atomicity); runs it directly, no transaction boundary at all, when it wasn't. {@link
     * Callable}, not a plain {@link java.util.function.Supplier}, specifically because {@code
     * CreateDataSource#createAll}/{@code UpdateDataSource#updateAll}/{@code
     * DeleteDataSource#deleteAll} all declare {@code throws Exception} - {@code
     * TransactionCallback#doInTransaction} has no {@code throws} clause of its own, so a checked
     * exception thrown inside has to be wrapped to escape the callback, then unwrapped back to
     * its original type once outside the transaction (any {@code RuntimeException} - including
     * the wrapper - already triggers rollback on the way out, which is exactly the point).
     */
    private <T> T inTransaction(Callable<T> work) throws Exception {
        if (transactionManager == null) {
            return work.call();
        }
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                try {
                    return work.call();
                } catch (Exception e) {
                    throw new TransactionRollbackWrapper(e);
                }
            });
        } catch (TransactionRollbackWrapper wrapper) {
            throw wrapper.cause;
        }
    }

    /**
     * Read counterpart to {@link #inTransaction} - wraps a single-entity/page fetch plus its
     * {@code Mapper}/{@code RestlessEmbedResolver} call in one {@code readOnly} transaction, so a
     * lazy JPA association a hand-written {@code Mapper} or {@code @RestlessEmbed} field touches
     * is still initializable when the consumer runs with {@code spring.jpa.open-in-view=false}
     * (Spring Boot's own OSIV default is {@code true}, which papers over exactly this - a
     * consumer who turns it off, the generally-recommended production setting, would otherwise
     * hit a {@link org.hibernate.LazyInitializationException} the moment mapping touched an
     * uninitialized proxy outside any session at all). {@code readOnly = true}: this path never
     * writes, so Hibernate can skip dirty-checking - a real (if modest) win, not just a label.
     * Same "no {@link PlatformTransactionManager} configured means no transaction boundary at
     * all" fallback as {@link #inTransaction} - unchanged behavior for every caller that
     * constructs a {@code RestlessResourceHandler} by hand (tests, mainly) rather than through
     * {@code RestlessRegistrar}.
     */
    private <T> T inReadOnlyTransaction(Callable<T> work) throws Exception {
        if (transactionManager == null) {
            return work.call();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        try {
            return template.execute(status -> {
                try {
                    return work.call();
                } catch (Exception e) {
                    throw new TransactionRollbackWrapper(e);
                }
            });
        } catch (TransactionRollbackWrapper wrapper) {
            throw wrapper.cause;
        }
    }

    /** See {@link #inTransaction}'s own javadoc for why this exists at all. */
    private static final class TransactionRollbackWrapper extends RuntimeException {
        private final Exception cause;

        TransactionRollbackWrapper(Exception cause) {
            this.cause = cause;
        }
    }

    // ---- RestlessEmbed support: called from another resource's RestlessEmbedResolver, never ----
    // ---- directly by RestlessRegistrar - see RestlessEmbed's javadoc. ----

    /**
     * Runs THIS resource's own {@code findList()} logic (coarse {@code preCheck}, {@code
     * joinFilter} ANDed with this resource's own {@code scope()}, fetch, map) for a caller
     * embedding it via {@link RestlessEmbed}. Public - {@link RestlessEmbedResolver} lives in a
     * different package and isn't a subclass. Unlike {@link #findList}, a denied {@code
     * preCheck()} returns an empty list rather than throwing: a relation the caller can't see
     * should read as "nothing here" on the *embedding* resource's response, not fail it outright.
     */
    public final List<?> findEmbeddedList(Specification<E> joinFilter, HttpServletRequest request) {
        if (!getAuthorizationGuard().preCheck(AuthorizationGuard.Action.READ_LIST, null, request)) {
            return List.of();
        }
        Specification<E> spec = withScope(joinFilter, AuthorizationGuard.Action.READ_LIST, null, request);
        return getEntityMapper().map(getReadDataSource().findAll(spec));
    }

    /**
     * Same as {@link #findEmbeddedList}, for a {@code many = false} {@link RestlessEmbed} field:
     * {@code joinFilter} is expected to match at most one row (a natural-key equality check, the
     * same assumption every other natural-key "join" in this codebase already makes) - the first
     * match if more than one somehow satisfies it. {@code null} (not 404/403) for no match, a
     * denied {@code preCheck}, or a denied {@code canAccess} on the row that did match.
     */
    public final Object findEmbeddedOne(Specification<E> joinFilter, HttpServletRequest request) {
        if (!getAuthorizationGuard().preCheck(AuthorizationGuard.Action.READ_ONE, null, request)) {
            return null;
        }
        List<E> matches = getReadDataSource().findAll(joinFilter);
        if (matches.isEmpty()) {
            return null;
        }
        E found = matches.get(0);
        if (!getAuthorizationGuard().canAccess(AuthorizationGuard.Action.READ_ONE, request, found)) {
            return null;
        }
        return getEntityMapper().map(found);
    }
}
