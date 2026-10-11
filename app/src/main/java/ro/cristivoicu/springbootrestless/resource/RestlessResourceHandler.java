package ro.cristivoicu.springbootrestless.resource;

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
import org.springframework.util.StringUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import ro.cristivoicu.springbootrestless.annotation.RestlessBulkMode;
import ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet;
import ro.cristivoicu.springbootrestless.annotation.RestlessDenial;
import ro.cristivoicu.springbootrestless.models.RestlessValidationGroups;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.authorization.RestlessPrincipalIdResolver;
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
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;
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
public abstract class RestlessResourceHandler<E, K> implements ro.cristivoicu.springbootrestless.error.RestlessErrorScope {

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
    private RestlessAuthorizationMetrics metrics = RestlessAuthorizationMetrics.NONE;
    private int maxListSize = DEFAULT_MAX_LIST_SIZE;
    private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    private int maxBulkSize = DEFAULT_MAX_BULK_SIZE;
    private boolean includeSoftDeletedInSingleRead = true;
    private String idPropertyName;

    /** Resolved in {@link #init}; never {@code DEFAULT}. */
    private RestlessBulkMode bulkMode = RestlessBulkMode.ALL_OR_NOTHING;

    /** Resolved in {@link #init}; never {@code DEFAULT}. */
    private RestlessCollectionGet collectionGet = RestlessCollectionGet.PAGE;

    /** {@link #getLifecycleHooks()}, resolved once in {@link #init}. */
    private List<RestlessLifecycleHook<E>> hooks = List.of();

    /** {@link #getAuthorizationGuard()}, resolved once in {@link #init} - see that method's own javadoc for why. */
    private AuthorizationGuard<E> cachedGuard;

    /**
     * Resolved once in {@link #init} from this resource's {@code @RestlessResource(parent = ...)}
     * - {@code null} for a flat (non-nested) resource, the overwhelmingly common case, which
     * leaves every nested-resource code path below a no-op. See {@link ParentFieldSupport}'s own
     * javadoc for what this actually describes and how it's used.
     */
    private ParentFieldSupport.ParentDescriptor parentDescriptor;

    private TransactionSupport transactionSupport;

    /** Resolved in {@link #init}: {@code @RestlessResource(denyAs)}, else {@code restless.authorization.deny-as}. Never {@code DEFAULT}. */
    private RestlessDenial denyAs = RestlessDenial.FORBIDDEN;

    /** {@code null} means {@link RestlessPrincipalIdResolver#AUTHENTICATION_NAME} - see {@link RestlessContext#principalId()}. */
    private RestlessPrincipalIdResolver principalIdResolver;

    /** This entity's {@code @RestlessFromPrincipal}/{@code @RestlessImmutable} fields - see {@link FieldRules}. */
    private FieldRules fieldRules = FieldRules.NONE;

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
     * {@link #pageableOf}'s default hard cap on the client-supplied {@code size} query parameter
     * for every paginated route ({@code findPage}/{@code findPageOverview}/{@code
     * findPageSelect}/{@code customRead}) - unlike {@link #DEFAULT_MAX_LIST_SIZE}, a request over
     * this cap is rejected with {@code 400}, not silently truncated, since there's an explicit
     * client-supplied value to validate against here (see {@code RestlessProperties.Page}'s own
     * javadoc for why that's the right tradeoff for a paginated route specifically). Matches
     * Spring Data's own {@code spring.data.web.pageable.max-page-size} default. Overridable via
     * {@code restless.page.max-size}.
     */
    public static final int DEFAULT_MAX_PAGE_SIZE = 2_000;

    /**
     * {@link #createBulk}/{@link #updateBulk}/{@link #deleteAll}'s default hard cap on how many
     * items a single bulk request may carry - checked, and rejected with {@code 400}, before any
     * of them are processed (same fail-fast-not-partial spirit {@link #inTransaction} already
     * applies to the write itself). Overridable via {@code restless.bulk.max-size}.
     */
    public static final int DEFAULT_MAX_BULK_SIZE = 1_000;

    /**
     * Wires this resource's infra collaborators. Called once by whichever registrar discovered
     * this bean (a hardcoded call in Stage 1, {@code RestlessRegistrar} from Stage 2 on) — kept
     * separate from the constructor so the entity author's subclass stays free of infra plumbing.
     * <p>
     * One {@link RestlessInitContext} parameter, not the telescoping chain of same-named
     * overloads this replaced (Ground rules Phase 2 item 14) - each of those existed purely so
     * an older caller/test kept compiling unchanged as one more collaborator was added over
     * time; a record names every field at the call site instead, so this never needs a seventh
     * overload the next time one more thing needs threading through.
     * <p>
     * Also resolves and caches {@link #getAuthorizationGuard()} into {@link #cachedGuard} here,
     * once - every internal call site ({@link #checkPreCheck}, {@link #checkCanAccess}, {@link
     * #withScope}, ...) reads that field instead of calling the (overridable, possibly
     * expensive-to-construct - see this method's own README-documented pattern) accessor again
     * on every single request.
     */
    public final void init(RestlessInitContext context) {
        this.metadata = context.metadata();
        this.objectMapper = context.objectMapper();
        this.conversionService = context.conversionService();
        this.validator = context.validator();
        this.embedResolver = context.embedResolver();
        this.transactionSupport = new TransactionSupport(context.transactionManager());
        this.metrics = context.metrics() == null ? RestlessAuthorizationMetrics.NONE : context.metrics();
        this.maxListSize = context.maxListSize() > 0 ? context.maxListSize() : DEFAULT_MAX_LIST_SIZE;
        this.maxPageSize = context.maxPageSize() > 0 ? context.maxPageSize() : DEFAULT_MAX_PAGE_SIZE;
        this.maxBulkSize = context.maxBulkSize() > 0 ? context.maxBulkSize() : DEFAULT_MAX_BULK_SIZE;
        this.includeSoftDeletedInSingleRead = context.includeSoftDeletedInSingleRead();
        this.idPropertyName = resolveIdPropertyName(metadata.entityType());
        this.denyAs = context.denyAs() == null || context.denyAs() == RestlessDenial.DEFAULT ? RestlessDenial.FORBIDDEN : context.denyAs();
        this.principalIdResolver = context.principalIdResolver();
        this.fieldRules = FieldRules.resolve(metadata.entityType(), context.rejectImmutableByDefault());
        this.cachedGuard = getAuthorizationGuard();
        this.hooks = List.copyOf(getLifecycleHooks());
        this.bulkMode = context.bulkMode() == null || context.bulkMode() == RestlessBulkMode.DEFAULT
                ? RestlessBulkMode.ALL_OR_NOTHING : context.bulkMode();
        this.collectionGet = context.collectionGet() == null || context.collectionGet() == RestlessCollectionGet.DEFAULT
                ? RestlessCollectionGet.PAGE : context.collectionGet();
        this.parentDescriptor = ParentFieldSupport.resolve(metadata.entityType(), context.parentPathVariable(), context.parentField());
        // Fail closed (Ground rules for this feature): a parent variable that doesn't actually
        // appear as a {variable} path segment in this resource's own basePath is a startup error,
        // never a silently unscoped route.
        if (this.parentDescriptor != null && !metadata.basePath().contains("{" + this.parentDescriptor.pathVariable() + "}")) {
            throw new IllegalStateException(getClass().getSimpleName() + ": @RestlessParent variable '"
                    + this.parentDescriptor.pathVariable() + "' does not appear in basePath '" + metadata.basePath() + "'");
        }
        // Not known yet when resolveMetadata() first built this.metadata (parent() is only read
        // here, in init()) - replaced with an enriched copy purely so RestlessOpenApiCustomizer
        // can document the parent path parameter on every nested route.
        if (this.parentDescriptor != null) {
            this.metadata = new ResourceMetadata(metadata.basePath(), metadata.entityType(), metadata.idType(),
                    metadata.createModelType(), metadata.updateModelType(), metadata.deleteModelType(),
                    metadata.searchDtoType(), metadata.responseDtoType(), metadata.overviewResponseDtoType(),
                    metadata.selectResponseDtoType(), metadata.version(),
                    this.parentDescriptor.pathVariable(), this.parentDescriptor.leafIdType());
        }
        this.searchDtoConstructor = resolveNoArgConstructor(metadata.searchDtoType(), "search DTO");
        resolveFilterBindings();

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
                version == null ? "" : version,
                // Not known yet at this point (resolveMetadata runs before init(), the only place
                // @RestlessResource(parent = ...) is actually read) - init() replaces this.metadata
                // with an enriched copy once parentDescriptor is resolved, purely so
                // RestlessOpenApiCustomizer has something to read later.
                null, null);
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
     * Named reads over the whole collection that aren't a page of rows - summaries, exports,
     * download links ({@code GET {basePath}/actions/{name}}), see {@link CollectionAction}. Empty by
     * default; names share the {@code /actions/} namespace with {@link #getCustomReadActions()}.
     * <b>Public</b> for the same reason as the other action accessors.
     */
    public Map<String, CollectionAction<E>> getCollectionActions() {
        return Map.of();
    }

    /** Side effects around this resource's writes - see {@link RestlessLifecycleHook}. Empty by default. */
    protected List<RestlessLifecycleHook<E>> getLifecycleHooks() {
        return List.of();
    }

    /**
     * Whether {@code POST {basePath}} also accepts {@code multipart/form-data}: the form fields and
     * files are bound onto the create model (a {@code MultipartFile} field receives the part of the
     * same name) and validated like a JSON body. {@code false} by default - a multipart request is
     * then a {@code 415}. Public so OpenAPI generation can document the second media type.
     */
    public boolean acceptsMultipartCreate() {
        return false;
    }

    /**
     * Duplicate detection for a {@code PARTIAL} bulk create - see {@link RestlessDedupeKey}.
     * {@code null} (default): no row is ever a duplicate.
     */
    public RestlessDedupeKey<E, ?> getBulkDeduplicator() {
        return null;
    }

    /** What {@code GET {basePath}} serves for this resource - resolved at startup, see {@link RestlessCollectionGet}. */
    public final RestlessCollectionGet getCollectionGet() {
        return collectionGet;
    }

    /** How {@code POST {basePath}/bulk} treats bad rows - resolved at startup, see {@link RestlessBulkMode}. */
    public final RestlessBulkMode getBulkMode() {
        return bulkMode;
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
     * One precomputed, startup-validated field-to-predicate binding for the default {@link
     * #getSpecification} - see {@link #resolveFilterBindings} for how these are built and {@link
     * #getSpecification}'s own javadoc for how they're used. {@code operator == null} means
     * plain equality on {@code entityProperty}; {@code field} has already had {@link
     * Field#setAccessible} called on it once, here, at startup - never per-request.
     */
    private record FilterBinding(Field field, String entityProperty, FilterOperator operator) {
    }

    private List<FilterBinding> filterBindings = List.of();

    /**
     * Default filter: an equality predicate for every non-null, non-blank field declared on the
     * {@code SearchDto} class hierarchy (stopping at, not including, {@link
     * ro.cristivoicu.springbootrestless.models.AbstractSearchDto} - its own paging/sort fields
     * are never filter candidates), ANDed together - unless the field's name ends with a
     * recognized {@link FilterOperator} suffix ({@code ageGte}, {@code nameLike}, {@code
     * statusIn}, ...), in which case that operator applies instead of equality. See {@code
     * docs/design/filter-dsl.md} for the full design (why a suffix convention rather than a
     * query-language string, and why the wire format is camelCase - {@code ?ageGte=30} - not
     * snake_case). Override for anything beyond these operators (joins, cross-field logic,
     * boolean OR, ...) - or use {@link ro.cristivoicu.springbootrestless.filter.RestlessSpecifications}
     * to write that override more tersely.
     * <p>
     * Every field/operator/entity-property combination is validated once, at startup, by {@link
     * #resolveFilterBindings} (called from {@link #init}) - not per request, and not silently
     * ignored on a mismatch (Ground rules item 5): a primitive filter field, an equality field
     * naming no real entity property, an operator whose base property doesn't resolve either
     * (directly or via the suffix-ambiguity fallback - see that method), an operator the
     * resolved property's type doesn't support, and {@code In} on a non-{@code Collection} field
     * all now fail fast there instead of being skipped or thrown from inside a running query.
     * This method itself only runs the precomputed bindings against one actual request's bound
     * {@code searchDto} values - unset (null/blank/empty-collection) fields are still skipped
     * per-request, same "absent means no filter" reasoning as before.
     */
    protected Specification<E> getSpecification(SearchDto searchDto) {
        List<FilterBinding> bindings = filterBindings;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (FilterBinding binding : bindings) {
                Object value;
                try {
                    value = binding.field().get(searchDto);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Could not read " + binding.field() + " for default filtering", e);
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

                if (binding.operator() == null) {
                    predicates.add(cb.equal(root.get(binding.entityProperty()), value));
                } else if (binding.operator() == FilterOperator.IN) {
                    predicates.add(root.<Object>get(binding.entityProperty()).in((Collection<?>) value));
                } else {
                    predicates.add(binding.operator().predicate(cb, root.get(binding.entityProperty()), value));
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds and validates {@link #filterBindings} once, at startup - see {@link
     * #getSpecification}'s own javadoc for what's being validated and why. Skipped entirely
     * (bindings left empty, never read) when this resource overrides {@link #getSpecification}
     * itself: validating a {@code SearchDto} shape against rules the actual, overridden filter
     * logic may not even follow would turn a legitimate hand-written override into a startup
     * failure it never asked for.
     */
    private void resolveFilterBindings() {
        if (!usesDefaultGetSpecification()) {
            return;
        }
        Set<String> entityProperties = entityPropertyNames();
        List<FilterBinding> bindings = new ArrayList<>();
        for (Field field : searchDtoFilterFields(metadata.searchDtoType())) {
            if (field.getType().isPrimitive()) {
                throw new IllegalStateException(metadata.searchDtoType().getSimpleName() + "." + field.getName()
                        + " is primitive - it can never represent \"the client didn't send this filter\" "
                        + "(Java always defaults it), so it would silently exclude every non-default row "
                        + "from an unfiltered search; use the boxed type instead");
            }
            field.setAccessible(true);

            FilterOperator operator = FilterOperator.forFieldName(field.getName());
            if (operator == null) {
                requireEntityProperty(field, field.getName(), entityProperties);
                bindings.add(new FilterBinding(field, field.getName(), null));
                continue;
            }

            // Suffix ambiguity (Ground rules item 5): "checkIn"/"loggedIn" parse as base "check"/
            // "logged" + the In suffix, which these entities may well not have a property named -
            // if the FULL field name is itself a real entity property, that takes priority and
            // this is plain equality, not an operator at all.
            if (entityProperties.contains(field.getName())) {
                bindings.add(new FilterBinding(field, field.getName(), null));
                continue;
            }

            String baseProperty = operator.basePropertyOf(field.getName());
            if (!entityProperties.contains(baseProperty)) {
                throw new IllegalStateException(metadata.searchDtoType().getSimpleName() + "." + field.getName()
                        + " resolves to neither entity property '" + field.getName() + "' nor '" + baseProperty
                        + "' on " + metadata.entityType().getSimpleName());
            }
            if (operator == FilterOperator.IN) {
                if (!Collection.class.isAssignableFrom(field.getType())) {
                    throw new IllegalStateException(field + " uses the 'In' suffix but isn't a Collection");
                }
                bindings.add(new FilterBinding(field, baseProperty, operator));
                continue;
            }
            Class<?> entityFieldType = entityFieldType(baseProperty);
            if (!operator.supports(entityFieldType)) {
                throw new IllegalStateException(field + " uses '" + operator + "' but "
                        + metadata.entityType().getSimpleName() + "." + baseProperty
                        + " (" + entityFieldType + ") doesn't support it");
            }
            bindings.add(new FilterBinding(field, baseProperty, operator));
        }
        this.filterBindings = bindings;
    }

    private void requireEntityProperty(Field field, String propertyName, Set<String> entityProperties) {
        if (!entityProperties.contains(propertyName)) {
            throw new IllegalStateException(metadata.searchDtoType().getSimpleName() + "." + field.getName()
                    + " (equality filter) names no property on " + metadata.entityType().getSimpleName());
        }
    }

    /**
     * Whether {@link #getSpecification} still runs this class's own default body - see {@link
     * #resolveFilterBindings}'s own javadoc for why this gates validation at all. {@code
     * getDeclaredMethod} (not {@code getMethod}, which only ever finds <em>public</em> methods -
     * {@link #getSpecification} is {@code protected}), walking up from the concrete runtime
     * class to find whichever one actually declares it.
     */
    private boolean usesDefaultGetSpecification() {
        for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod("getSpecification", SearchDto.class);
                return type == RestlessResourceHandler.class;
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        throw new IllegalStateException("getSpecification(SearchDto) not found on " + getClass()); // unreachable
    }

    /**
     * Every non-static field declared on {@code searchDtoType}'s own class hierarchy, stopping
     * at (not including) {@link ro.cristivoicu.springbootrestless.models.AbstractSearchDto} -
     * unlike {@link #entityPropertyNames}'s walk-to-{@code Object}, a {@code SearchDto} hierarchy
     * has a real, known stopping point: {@code AbstractSearchDto}'s own {@code page}/{@code
     * size}/{@code sort} fields are never filter candidates, suffix-named or not.
     */
    private static List<Field> searchDtoFilterFields(Class<?> searchDtoType) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = searchDtoType;
             type != null && type != Object.class && type != ro.cristivoicu.springbootrestless.models.AbstractSearchDto.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
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

    /**
     * Atomic write pipeline (Ground rules item 1): {@link #inTransaction} now covers the write,
     * the row-level {@link AuthorizationGuard#canAccess} check, the flush, and {@link
     * Mapper#map} as one unit - previously none of this ran inside any transaction at all, so a
     * write-response {@code Mapper} touching a lazy association failed under {@code
     * spring.jpa.open-in-view=false} (see {@code CreateWithLazyAssociationOsivOffTest}).
     * <p>
     * Row-level authorization on writes (Ground rules item 2): unlike update/patch/delete,
     * create has no pre-image to check - the very first check against the real, attributed
     * entity (not {@link #checkPreCheck}'s coarse, pre-creation one) happens <em>after</em> the
     * data source saves it, still inside the same transaction. A denial throws, which rolls the
     * insert back - the row never actually exists from any other transaction's point of view.
     */
    public final ResponseEntity<?> create(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.CREATE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.CREATE, null, request);
            Object body = isMultipart(request) ? bindMultipart(request, metadata.createModelType()) : readBody(request, metadata.createModelType());
            validate(body, RestlessValidationGroups.OnCreate.class);
            // Raw-type escape hatch: C's bound (CreateModel) can't be named here since the actual
            // type is only known at runtime via metadata.createModelType(). Safe because `body` was
            // just deserialized as exactly that class.
            @SuppressWarnings({"unchecked", "rawtypes"})
            CreateDataSource rawDataSource = getCreateDataSource();

            return transactionSupport.inTransaction(() -> {
                @SuppressWarnings("unchecked")
                E created = (E) rawDataSource.create((CreateModel) body, ctx);
                // Nested resources (Feature B): the parent id in the path is written AFTER the
                // create model is applied - a body-supplied value for the same field is always
                // overwritten, never honored. See ParentFieldSupport's own javadoc.
                writeParentFieldIfNested(created, ctx);
                // Same "written after, whatever the data source did" guarantee for principal fields.
                fieldRules.applyPrincipal(created, ctx, conversionService);
                checkCanAccess(AuthorizationGuard.Action.CREATE, request, created);
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.afterCreate(created, ctx);
                }
                rawDataSource.flush();
                // 201 + Location, not 200: RFC 9110 §15.3.2 - a successful POST that creates a
                // resource should report 201 and point at where the new resource can be fetched.
                // idOf(created) can come back null for an entity with no @Id field reachable via
                // reflection walk-up (shouldn't happen for a real JPA entity, but a hand-rolled test
                // double might skip it) - falls back to plain 200 with no Location rather than
                // building a broken URI in that case.
                Object dto = postProcessResponse(AuthorizationGuard.Action.CREATE, null, request, created, getEntityMapper().map(created));
                Object id = idOf(created);
                if (id != null) {
                    java.net.URI location = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                            .fromRequest(request).path("/{id}").buildAndExpand(id).toUri();
                    return withETag(ResponseEntity.created(location), created).body(dto);
                }
                return withETag(ResponseEntity.ok(), created).body(dto);
            });
        });
    }

    /** Sets the {@code ETag} header from {@code entity}'s {@code @Version} (Ground rules Phase 2 item 9) - a no-op builder pass-through when the entity has none. */
    private ResponseEntity.BodyBuilder withETag(ResponseEntity.BodyBuilder builder, E entity) {
        return PreconditionSupport.eTagOf(entity).map(builder::eTag).orElse(builder);
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
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.CREATE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.CREATE, null, request);
            List<Object> bodies = readBodyList(request, metadata.createModelType());
            checkBulkSize(bodies.size());
            @SuppressWarnings({"unchecked", "rawtypes"})
            CreateDataSource rawDataSource = getCreateDataSource();
            if (bulkMode == RestlessBulkMode.PARTIAL) {
                return createBulkPartial(request, ctx, bodies, rawDataSource);
            }
            for (Object body : bodies) {
                validate(body, RestlessValidationGroups.OnCreate.class);
            }
            // Row-level authorization on writes (Ground rules item 2) extends to bulk create too -
            // otherwise this route would be a direct bypass of the same check single create() now
            // runs. Every created item is checked (fail-fast, before the response goes out) inside
            // the same transaction the write itself ran in, so a denial rolls back the whole batch.
            return transactionSupport.inTransaction(() -> {
                @SuppressWarnings("unchecked")
                List<E> created = rawDataSource.createAll(bodies, ctx);
                for (E entity : created) {
                    writeParentFieldIfNested(entity, ctx);
                    fieldRules.applyPrincipal(entity, ctx, conversionService);
                    checkCanAccess(AuthorizationGuard.Action.CREATE, request, entity);
                    for (RestlessLifecycleHook<E> hook : hooks) {
                        hook.afterCreate(entity, ctx);
                    }
                }
                rawDataSource.flush();
                return ResponseEntity.ok(getEntityMapper().map(created));
            });
        });
    }

    /**
     * Conditional GET (Ground rules Phase 2 item 9): emits a strong {@code ETag} from {@code
     * @Version} on the {@code 200}, and - if the client sent {@code If-None-Match} and it
     * matches (weak comparison, per RFC 9110 §13.1.2 for a {@code GET}) - short-circuits to
     * {@code 304} with that same {@code ETag} and no body, after the row is loaded and
     * guard-checked (so a {@code 304} still correctly 404s/403s for a missing/forbidden row
     * rather than leaking "this exists" to a caller who can't otherwise see it) but before
     * mapping/embed-resolving a body that's about to be thrown away anyway.
     */
    public final ResponseEntity<?> findOne(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_ONE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.READ_ONE, null, request);
            K id = extractId(request);
            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            // inReadOnlyTransaction: getEntityMapper().map(...) and embedResolver.resolve(...) both
            // run inside it - see that method's own javadoc for why a lazy association needs this.
            return transactionSupport.inReadOnlyTransaction(() -> {
                E found = getReadDataSource().findOne(id, ctx);
                if (found == null || (isSoftDeleted(found) && !includeSoftDeletedInSingleRead) || parentMismatch(found, ctx)) {
                    return ResponseEntity.notFound().build();
                }
                if (!canRead(AuthorizationGuard.Action.READ_ONE, null, request, found)) {
                    return ResponseEntity.notFound().build();
                }
                Optional<String> etag = PreconditionSupport.eTagOf(found);
                if (etag.isPresent() && PreconditionSupport.matchesIfNoneMatch(ifNoneMatch, etag.get())) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag.get()).build();
                }
                Object dto = postProcessResponse(AuthorizationGuard.Action.READ_ONE, null, request, found, getEntityMapper().map(found));
                // Opt-in (?expand=name,...), see RestlessEmbed - a no-op for a request that doesn't
                // ask for anything, and for a resource whose DTO declares no @RestlessEmbed field.
                embedResolver.resolve(dto, found, request);
                return withETag(ResponseEntity.ok(), found).body(dto);
            });
        });
    }

    public final ResponseEntity<List<?>> findList(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_LIST, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.READ_LIST, null, request);
            SearchDto searchDto = bindSearchDto(searchDtoConstructor, request);
            Specification<E> spec = withScope(withParentFilter(excludeSoftDeleted(getSpecification(searchDto)), ctx),
                    AuthorizationGuard.Action.READ_LIST, null, request);
            // Capped, not a plain findAll(spec): see maxListSize's own javadoc for why an unpaginated
            // route needs a hard limit at all. One extra row requested (maxListSize + 1) so "hit the
            // cap" can be told apart from "exactly maxListSize rows existed" without a second COUNT
            // query - the (maxListSize + 1)-th row itself is trimmed back off before mapping.
            Sort sort = pageableOf(searchDto).getSort();
            // inReadOnlyTransaction: getOverviewMapper().map(...) runs inside it too - see that
            // method's own javadoc for why a lazy association needs this.
            return transactionSupport.inReadOnlyTransaction(() -> {
                List<E> data = getReadDataSource().findAll(spec, org.springframework.data.domain.PageRequest.of(0, maxListSize + 1, sort), ctx).getContent();
                boolean truncated = data.size() > maxListSize;
                List<E> page = truncated ? data.subList(0, maxListSize) : data;
                ResponseEntity.BodyBuilder response = truncated
                        ? ResponseEntity.ok().header("X-Restless-List-Truncated", "true")
                        : ResponseEntity.ok();
                return response.body(getOverviewMapper().map(page));
            });
        });
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPage(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_PAGE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.READ_PAGE, null, request);
            return ResponseEntity.ok(paginate(request, getEntityMapper(), AuthorizationGuard.Action.READ_PAGE, ctx));
        });
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPageOverview(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_PAGE_OVERVIEW, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.READ_PAGE_OVERVIEW, null, request);
            return ResponseEntity.ok(paginate(request, getOverviewMapper(), AuthorizationGuard.Action.READ_PAGE_OVERVIEW, ctx));
        });
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPageSelect(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_PAGE_SELECT, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.READ_PAGE_SELECT, null, request);
            return ResponseEntity.ok(paginate(request, getSelectMapper(), AuthorizationGuard.Action.READ_PAGE_SELECT, ctx));
        });
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
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.CUSTOM_READ, actionName);
        return RestlessContext.runWith(ctx, () -> {
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
            spec = withScope(withParentFilter(excludeSoftDeleted(spec), ctx), AuthorizationGuard.Action.CUSTOM_READ, actionName, request);

            return ResponseEntity.ok(paginate(getOverviewMapper(), spec, pageableOf(searchDto), ctx));
        });
    }

    /**
     * Shared entry point for every {@link CollectionAction} - same one-{@link Method}-per-name
     * dispatch as {@link #customRead}. The action gets the caller's visible rows as a
     * specification (parent filter, guard scope, soft delete) and returns any body: JSON, or a
     * {@link RestlessBinary} written straight to the response. Runs in a read-only transaction.
     */
    public final ResponseEntity<?> collectionAction(HttpServletRequest request) throws Exception {
        String actionName = resolveActionName(request);
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.CUSTOM_READ, actionName);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.CUSTOM_READ, actionName, request);
            CollectionAction<E> action = getCollectionActions().get(actionName);
            if (action == null) {
                return ResponseEntity.notFound().build();
            }
            Specification<E> visibleRows = withScope(withParentFilter(excludeSoftDeleted((root, query, cb) -> cb.conjunction()), ctx),
                    AuthorizationGuard.Action.CUSTOM_READ, actionName, request);
            return transactionSupport.inReadOnlyTransaction(() -> {
                Object result = action.execute(visibleRows, ctx);
                return result instanceof RestlessBinary binary ? writeBinary(binary) : ResponseEntity.ok(result);
            });
        });
    }

    /**
     * Writes a {@link RestlessBinary} directly to the servlet response - synchronously, so it runs
     * inside the caller's transaction - and returns {@code null}, which tells Spring MVC the
     * response is already handled.
     */
    private ResponseEntity<?> writeBinary(RestlessBinary binary) throws java.io.IOException {
        jakarta.servlet.http.HttpServletResponse response =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getResponse();
        if (response == null) {
            throw new IllegalStateException("No servlet response bound to this request - a RestlessBinary needs one");
        }
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(binary.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, binary.contentDisposition());
        if (binary.length() != null) {
            response.setContentLengthLong(binary.length());
        }
        binary.writer().writeTo(response.getOutputStream());
        response.flushBuffer();
        return null;
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
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.NAMED_VIEW, viewName);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.NAMED_VIEW, viewName, request);
            K id = extractId(request);
            // inReadOnlyTransaction: mapper.map(...)/embedResolver.resolve(...) run inside it too -
            // see that method's own javadoc for why a lazy association needs this.
            return transactionSupport.inReadOnlyTransaction(() -> {
                E found = getReadDataSource().findOne(id, ctx);
                if (found == null || (isSoftDeleted(found) && !includeSoftDeletedInSingleRead) || parentMismatch(found, ctx)) {
                    return ResponseEntity.notFound().build();
                }
                if (!canRead(AuthorizationGuard.Action.NAMED_VIEW, viewName, request, found)) {
                    return ResponseEntity.notFound().build();
                }
                Mapper<E, ?> mapper = getNamedViews().get(viewName);
                if (mapper == null) {
                    return ResponseEntity.notFound().build();
                }
                Object view = mapper.map(found);
                if (view instanceof RestlessBinary binary) {
                    return writeBinary(binary);
                }
                Object dto = postProcessResponse(AuthorizationGuard.Action.NAMED_VIEW, viewName, request, found, view);
                embedResolver.resolve(dto, found, request);
                return withETag(ResponseEntity.ok(), found).body(dto);
            });
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
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.WRITE_ACTION, actionName);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.WRITE_ACTION, actionName, request);
            WriteAction<E, ?, ?> action = getCustomWriteActions().get(actionName);
            if (action == null) {
                return ResponseEntity.notFound().build();
            }

            K id = extractId(request);
            // Ground rules Phase 2 item 9: If-Match is now optionally honored on a named write
            // action too - previously never checked here at all, regardless of whether the client
            // sent one. "Optional" means unchanged no-op-when-absent semantics, same as every other
            // single-item write.
            String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
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

            return transactionSupport.inTransaction(() -> {
                E found = getReadDataSource().findOne(id, ctx);
                if (found == null || isSoftDeleted(found) || parentMismatch(found, ctx)) {
                    return ResponseEntity.notFound().build();
                }
                checkCanAccess(AuthorizationGuard.Action.WRITE_ACTION, actionName, request, found);
                PreconditionSupport.checkIfMatch(ifMatch, found);
                @SuppressWarnings("unchecked")
                Object response = rawAction.execute(found, body);
                return response instanceof RestlessBinary binary ? writeBinary(binary) : ResponseEntity.ok(response);
            });
        });
    }

    /**
     * Atomic write pipeline (Ground rules item 1): load, 404-if-missing, {@code canAccess},
     * {@code If-Match}, the data-source write, flush, and {@code Mapper.map} all now run inside
     * one {@link #inTransaction} boundary, in that order - previously the guard/{@code If-Match}
     * check loaded the entity separately, outside any transaction, and {@code
     * UpdateDataSource#update} reloaded it again independently to actually write. Two consequences
     * of that gap, now closed: a concurrent write landing between the check and this request's own
     * write could leave the check's decision based on data already stale by write time (see
     * {@code ConcurrentUpdateRaceTest} - the data source's own {@code findById} now hits this same
     * transaction's persistence context instead of re-querying, so a genuine conflict surfaces as
     * {@code 409}/{@code 412}, never a silent overwrite); and the write-then-map sequence had no
     * transaction/session to run in at all under {@code spring.jpa.open-in-view=false} (see {@code
     * CreateWithLazyAssociationOsivOffTest}). The load that used to be skipped entirely when
     * {@code !hasGuard() && ifMatch == null} now always happens - the data source's own internal
     * reload being a persistence-context hit, not an extra query, is exactly what makes that an
     * acceptable one-SELECT-per-write cost rather than a doubled one.
     * <p>
     * Row-level authorization on writes (Ground rules item 2): the existing pre-image {@link
     * AuthorizationGuard#canAccess} check (unchanged) runs before the write; {@link
     * AuthorizationGuard#canAccessAfterWrite} is new, and runs after it, on the now-mutated
     * entity - catching a transition the pre-image check alone can't (e.g. a client who owns a
     * row reassigning it to someone else's account). A denial throws, rolling the whole write
     * back.
     */
    public final ResponseEntity<?> update(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.UPDATE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.UPDATE, null, request);
            K id = extractId(request);
            String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
            Object body = readBody(request, metadata.updateModelType());
            validate(body, RestlessValidationGroups.OnUpdate.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            UpdateDataSource rawDataSource = getUpdateDataSource();

            return transactionSupport.inTransaction(() -> {
                E existing = getReadDataSource().findOne(id, ctx);
                if (existing == null || isSoftDeleted(existing) || parentMismatch(existing, ctx)) {
                    // Same ResponseStatusException/ProblemDetail shape DefaultUpdateDataSource's own
                    // not-found used to throw (now redundant there, but every hand-written
                    // UpdateDataSource is still free to also throw it for the same id) - not a bare
                    // 404, to keep this byte-for-byte compatible with before this pipeline existed.
                    // A soft-deleted row 404s here unconditionally (Ground rules Phase 2 item 13) -
                    // see isSoftDeleted's own javadoc for why a write never consults
                    // includeSoftDeletedInSingleRead the way findOne/namedView do. A foreign-parent
                    // row 404s the same way (Feature B item 4) - from the caller's point of view a
                    // row scoped to someone else's parent is exactly as absent as one that was
                    // never there.
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity " + id + " not found");
                }
                checkCanAccess(AuthorizationGuard.Action.UPDATE, request, existing);
                PreconditionSupport.checkIfMatch(ifMatch, existing);
                // Taken before the data source runs: it usually mutates this same managed instance.
                Map<Field, Object> immutableValues = fieldRules.snapshot(existing);
                fieldRules.rejectChanges(body, immutableValues);
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.beforeUpdate(existing, ctx);
                }
                @SuppressWarnings("unchecked")
                E updated = (E) rawDataSource.update(id, (UpdateModel) body, ctx);
                // The parent field can never change via update (Feature B item 5 - "protected
                // like @Id") - forcibly reset regardless of what the data source did with it.
                writeParentFieldIfNested(updated, ctx);
                fieldRules.restore(updated, immutableValues);
                checkCanAccessAfterWrite(AuthorizationGuard.Action.UPDATE, request, updated);
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.afterUpdate(updated, ctx);
                }
                rawDataSource.flush();
                Object dto = postProcessResponse(AuthorizationGuard.Action.UPDATE, null, request, updated, getEntityMapper().map(updated));
                return withETag(ResponseEntity.ok(), updated).body(dto);
            });
        });
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
    /** Same atomic-write-pipeline/post-image-check shape as {@link #update} - see its own javadoc for the full reasoning. */
    public final ResponseEntity<?> patch(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.PATCH, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.PATCH, null, request);
            K id = extractId(request);
            String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
            Object body = readBody(request, patchModelType);
            validate(body, RestlessValidationGroups.OnPatch.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            PatchDataSource rawDataSource = getPatchDataSource().orElseThrow(
                    () -> new IllegalStateException("PATCH route registered but getPatchDataSource() is now empty"));

            return transactionSupport.inTransaction(() -> {
                E existing = getReadDataSource().findOne(id, ctx);
                if (existing == null || isSoftDeleted(existing) || parentMismatch(existing, ctx)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity " + id + " not found");
                }
                checkCanAccess(AuthorizationGuard.Action.PATCH, request, existing);
                PreconditionSupport.checkIfMatch(ifMatch, existing);
                Map<Field, Object> immutableValues = fieldRules.snapshot(existing);
                fieldRules.rejectChanges(body, immutableValues);
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.beforeUpdate(existing, ctx);
                }
                @SuppressWarnings("unchecked")
                E patched = (E) rawDataSource.patch(id, (PatchModel) body, ctx);
                // Protected like @Id, same as update() - see Feature B item 5.
                writeParentFieldIfNested(patched, ctx);
                fieldRules.restore(patched, immutableValues);
                checkCanAccessAfterWrite(AuthorizationGuard.Action.PATCH, request, patched);
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.afterUpdate(patched, ctx);
                }
                rawDataSource.flush();
                Object dto = postProcessResponse(AuthorizationGuard.Action.PATCH, null, request, patched, getEntityMapper().map(patched));
                return withETag(ResponseEntity.ok(), patched).body(dto);
            });
        });
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
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.UPDATE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.UPDATE, null, request);
            Map<String, Object> rawBodies = readBodyMap(request, metadata.updateModelType());
            checkBulkSize(rawBodies.size());

            Map<K, Object> byId = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawBodies.entrySet()) {
                validate(entry.getValue(), RestlessValidationGroups.OnUpdate.class);
                byId.put(convertId(entry.getKey()), entry.getValue());
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            UpdateDataSource rawDataSource = getUpdateDataSource();
            return transactionSupport.inTransaction(() -> {
                // Ground rules Phase 2 item 10: one findAllById query instead of N findOne calls,
                // and one canAccessAll check instead of looping checkCanAccess - see both methods'
                // own javadoc (CerbosAuthorizationGuard overrides canAccessAll with one batch() RPC).
                // Also runs (even with no guard configured) when this is a nested resource -
                // Feature B item 4: any target missing/foreign-parent is 404 for the whole batch,
                // nothing written, checked here, before any write.
                // Immutable fields: every target's stored values, keyed by id, taken before any write.
                Map<Object, Map<Field, Object>> immutableValues = new HashMap<>();
                if (hasGuard() || isNested() || !fieldRules.isEmpty() || !hooks.isEmpty()) {
                    List<E> loaded = getReadDataSource().findAllById(byId.keySet(), ctx);
                    checkAllFoundAndParentMatchedOrFail(byId.keySet(), loaded, ctx);
                    if (hasGuard()) {
                        checkCanAccessAll(AuthorizationGuard.Action.UPDATE, request, loaded);
                    }
                    if (!fieldRules.isEmpty()) {
                        for (E entity : loaded) {
                            Map<Field, Object> snapshot = fieldRules.snapshot(entity);
                            fieldRules.rejectChanges(byId.get(idOf(entity)), snapshot);
                            immutableValues.put(idOf(entity), snapshot);
                        }
                    }
                    for (E entity : loaded) {
                        for (RestlessLifecycleHook<E> hook : hooks) {
                            hook.beforeUpdate(entity, ctx);
                        }
                    }
                }
                @SuppressWarnings("unchecked")
                List<E> updated = rawDataSource.updateAll(byId, ctx);
                // Row-level authorization on writes (Ground rules item 2), same post-image check
                // single update() now runs, extended to every item in the batch - otherwise this
                // route would be a direct bypass of it.
                for (E entity : updated) {
                    writeParentFieldIfNested(entity, ctx);
                    fieldRules.restore(entity, immutableValues.getOrDefault(idOf(entity), Map.of()));
                    checkCanAccessAfterWrite(AuthorizationGuard.Action.UPDATE, request, entity);
                    for (RestlessLifecycleHook<E> hook : hooks) {
                        hook.afterUpdate(entity, ctx);
                    }
                }
                rawDataSource.flush();
                return ResponseEntity.ok(getEntityMapper().map(updated));
            });
        });
    }

    /**
     * Same atomic-write-pipeline shape as {@link #update} (Ground rules item 1): load,
     * 404-if-missing, {@code canAccess}, {@code If-Match}, then the actual delete, all inside one
     * {@link #inTransaction}. No post-image check (item 2 only applies to update/patch/create -
     * there's no "after" state for a deleted row) and no {@code Mapper.map} (a {@code 204} has no
     * body), but the flush still runs, so the delete has genuinely landed before the response
     * goes out. Previously the guard/{@code If-Match} load ran separately, outside any
     * transaction, and a missing row fell through to whatever {@code DeleteDataSource#deleteById}
     * happened to do with an absent id (the default throws an uncaught {@code
     * EmptyResultDataAccessException} - a {@code 500} - rather than this clean {@code 404}).
     */
    public final ResponseEntity<?> deleteById(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.DELETE_ONE, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.DELETE_ONE, null, request);
            K id = extractId(request);
            String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
            DeleteDataSource<E, K, ?> dataSource = getDeleteDataSource();

            return transactionSupport.inTransaction(() -> {
                E existing = getReadDataSource().findOne(id, ctx);
                if (existing == null || isSoftDeleted(existing) || parentMismatch(existing, ctx)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity " + id + " not found");
                }
                checkCanAccess(AuthorizationGuard.Action.DELETE_ONE, request, existing);
                PreconditionSupport.checkIfMatch(ifMatch, existing);
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.beforeDelete(existing, ctx);
                }
                dataSource.deleteById(id, ctx);
                dataSource.flush();
                for (RestlessLifecycleHook<E> hook : hooks) {
                    hook.afterDelete(existing, ctx);
                }
                return ResponseEntity.noContent().build();
            });
        });
    }

    public final ResponseEntity<?> deleteAll(HttpServletRequest request) throws Exception {
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.DELETE_ALL, null);
        return RestlessContext.runWith(ctx, () -> {
            checkPreCheck(AuthorizationGuard.Action.DELETE_ALL, null, request);
            Object body = readBody(request, metadata.deleteModelType());
            DeleteModel deleteModel = (DeleteModel) body;
            checkBulkSize(deleteModel.getIds().size());
            @SuppressWarnings({"unchecked", "rawtypes"})
            DeleteDataSource rawDataSource = getDeleteDataSource();
            // Fail-fast, before deleting anything: check every targeted entity up front so a bulk
            // delete never partially completes before hitting a denied id. Skipped entirely (the
            // whole check, not just part of it) when no guard is configured and this isn't a
            // nested resource. Both the check and the actual delete run inside the same
            // transaction (see #inTransaction) - a row can't change between being checked and
            // being deleted either. Ground rules Phase 2 item 10: one findAllById query instead of
            // N findOne calls, one canAccessAll check instead of looping checkCanAccess. Feature B
            // item 4: any target missing/foreign-parent is 404 for the whole batch, nothing
            // deleted.
            transactionSupport.<Void>inTransaction(() -> {
                List<E> loaded = List.of();
                if (hasGuard() || isNested() || !hooks.isEmpty()) {
                    List<K> ids = deleteModel.getIds().stream().map(this::convertId).toList();
                    loaded = getReadDataSource().findAllById(ids, ctx);
                    checkAllFoundAndParentMatchedOrFail(ids, loaded, ctx);
                    if (hasGuard()) {
                        checkCanAccessAll(AuthorizationGuard.Action.DELETE_ALL, request, loaded);
                    }
                }
                for (E entity : loaded) {
                    for (RestlessLifecycleHook<E> hook : hooks) {
                        hook.beforeDelete(entity, ctx);
                    }
                }
                rawDataSource.deleteAll(deleteModel, ctx);
                rawDataSource.flush();
                for (E entity : loaded) {
                    for (RestlessLifecycleHook<E> hook : hooks) {
                        hook.afterDelete(entity, ctx);
                    }
                }
                return null;
            });
            return ResponseEntity.noContent().build();
        });
    }

    // ---- shared per-request parsing, reusing Spring's own infra rather than hand-rolling it ----

    private PageableResponse<List<?>> paginate(HttpServletRequest request, Mapper<E, ?> mapper, AuthorizationGuard.Action action, RestlessContext ctx) throws Exception {
        SearchDto searchDto = bindSearchDto(searchDtoConstructor, request);
        Specification<E> spec = withScope(withParentFilter(excludeSoftDeleted(getSpecification(searchDto)), ctx), action, null, request);
        return paginate(mapper, spec, pageableOf(searchDto), ctx);
    }

    /**
     * Shared by {@link #findPage}/{@link #findPageOverview}/{@link #findPageSelect} (via the
     * request-binding overload above, using {@link #getSpecification}) and {@link #customRead}
     * (using a {@link ReadAction}'s own specification) — pagination/response-shaping logic is
     * identical either way, only the filter source and mapper differ.
     */
    private PageableResponse<List<?>> paginate(Mapper<E, ?> mapper, Specification<E> spec, Pageable pageable, RestlessContext ctx) throws Exception {
        // inReadOnlyTransaction: mapper.map(...) runs inside it too - see that method's own
        // javadoc for why a lazy association needs this. Shared by findPage/findPageOverview/
        // findPageSelect/customRead (via the two callers below), so this one wrap covers all four.
        return transactionSupport.inReadOnlyTransaction(() -> {
            Page<E> page = getReadDataSource().findAll(spec, pageable, ctx);

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

        if (pageable.getPageSize() > maxPageSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Requested page size " + pageable.getPageSize() + " exceeds the maximum of " + maxPageSize);
        }

        // AbstractSearchDto.getPageable() always defaults to a literal "id" sort when the client
        // sends none - wrong (and a guaranteed 400 below) for an entity whose @Id isn't literally
        // named "id". Only for that base class (best-effort, same "known base, not the SearchDto
        // interface in general" scope every other AbstractSearchDto-specific idiom here has): swap
        // in the entity's real @Id property, and append it as a tie-breaker to an explicit sort
        // that doesn't already end in it (a prerequisite for stable pagination - two rows tied on
        // every client-requested sort key would otherwise have no guaranteed relative order at
        // all between pages).
        if (searchDto instanceof ro.cristivoicu.springbootrestless.models.AbstractSearchDto abstractSearchDto) {
            Sort normalized = applyIdDefaultAndTieBreaker(abstractSearchDto.getSort(), pageable.getSort(), idPropertyName);
            pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), normalized);
        }

        Set<String> sortableProperties = sortablePropertyNames(metadata.entityType(), metadata.responseDtoType());
        for (Sort.Order order : pageable.getSort()) {
            if (!sortableProperties.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown sort property '" + order.getProperty() + "' for " + metadata.entityType().getSimpleName());
            }
        }
        return pageable;
    }

    /**
     * Pure logic extracted out of {@link #pageableOf} purely so {@code
     * RestlessResourceHandlerSortTest} can assert on the exact {@link Sort} produced without a
     * database in the loop - two rows tied on every client-requested sort key coincidentally come
     * back in insertion/id order on a tiny H2 table regardless of whether a tie-breaker was
     * actually appended, so observed row order can't reliably prove this logic either way.
     * {@code clientSortClauses}: the client's raw, pre-{@code Pageable} sort clauses ({@code
     * AbstractSearchDto#getSort()}) - empty means "the client specified no sort at all" (not
     * "sorted by nothing," which {@code AbstractSearchDto#getPageable()} never actually produces).
     * Package-private for that same test.
     */
    static Sort applyIdDefaultAndTieBreaker(List<String> clientSortClauses, Sort boundSort, String idProperty) {
        if (clientSortClauses.isEmpty()) {
            return Sort.by(Sort.Direction.ASC, idProperty);
        }
        List<Sort.Order> orders = boundSort.toList();
        boolean endsInId = !orders.isEmpty() && orders.get(orders.size() - 1).getProperty().equals(idProperty);
        return endsInId ? boundSort : boundSort.and(Sort.by(Sort.Direction.ASC, idProperty));
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

    /**
     * {@link #entityPropertyNames}, narrowed to what's actually safe to sort by (Ground rules
     * item 4): associations/collections excluded (ordering a JPA criteria query by a related
     * entity or a collection either fails outright or means something far less obvious than
     * "compare this column"), statics and {@code transient}/{@code @Transient} fields excluded
     * (nothing backing them in a column to order by at all), and - unlike {@link
     * #entityPropertyNames}, used for filter-DSL validation, which has no such concern - any
     * property whose {@code responseDtoType} counterpart is marked hidden, since sorting by a
     * masked field leaks its order to a caller who can't see its value. Matched by annotation
     * <em>simple name</em> ({@code "CerbosHiddenField"}), not type: this module can't depend on
     * the optional {@code cerbos} module that actually declares it (same reasoning {@code
     * RestlessMapperExclude}'s own cross-module checks elsewhere already accept) - see
     * {@code RestlessResourceHandlerSortTest} for a self-contained proof using a same-named local
     * stand-in. {@code responseDtoType} may be {@code null} (an unreifiable {@code Mapper}, see
     * {@link ResourceMetadata}'s own javadoc) - that exclusion is simply skipped then, not an
     * error. Static (not instance) and side-effect-free, so a unit test can exercise it directly
     * against arbitrary fixture classes with no {@code RestlessResourceHandler} instance at all.
     */
    static Set<String> sortablePropertyNames(Class<?> entityType, Class<?> responseDtoType) {
        Set<String> hiddenByDto = responseDtoType == null ? Set.of() : maskedFieldNames(responseDtoType);
        Set<String> names = new HashSet<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isTransient(modifiers)) {
                    continue;
                }
                if (field.isAnnotationPresent(jakarta.persistence.Transient.class)) {
                    continue;
                }
                if (isAssociationOrCollection(field)) {
                    continue;
                }
                if (hiddenByDto.contains(field.getName())) {
                    continue;
                }
                names.add(field.getName());
            }
        }
        return names;
    }

    private static boolean isAssociationOrCollection(Field field) {
        if (field.isAnnotationPresent(jakarta.persistence.OneToMany.class)
                || field.isAnnotationPresent(jakarta.persistence.ManyToMany.class)
                || field.isAnnotationPresent(jakarta.persistence.OneToOne.class)
                || field.isAnnotationPresent(jakarta.persistence.ManyToOne.class)
                || field.isAnnotationPresent(jakarta.persistence.ElementCollection.class)) {
            return true;
        }
        return Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType());
    }

    private static Set<String> maskedFieldNames(Class<?> dtoType) {
        Set<String> hidden = new HashSet<>();
        for (Class<?> type = dtoType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                for (java.lang.annotation.Annotation annotation : field.getAnnotations()) {
                    if (annotation.annotationType().getSimpleName().equals("CerbosHiddenField")) {
                        hidden.add(field.getName());
                    }
                }
            }
        }
        return hidden;
    }

    /**
     * The entity's own {@code @jakarta.persistence.Id} property name, walking superclasses the
     * same way {@link #entityPropertyNames} does - resolved once in {@link #init} (cheap enough
     * to not matter, but every other per-request reflection walk in this class is already a
     * fresh computation rather than a cached one, so this one is too, for consistency). {@link
     * #pageableOf} is the one reader.
     */
    private static String resolveIdPropertyName(Class<?> entityType) {
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    return field.getName();
                }
            }
        }
        throw new IllegalStateException("No @Id field found on " + entityType);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/");
    }

    /**
     * Multipart create: binds form fields and file parts onto a fresh {@code type} instance with the
     * same {@link ServletRequestDataBinder} search DTOs use - a {@code MultipartFile} property
     * receives the part with its name. {@code 415} unless {@link #acceptsMultipartCreate()}.
     */
    private Object bindMultipart(HttpServletRequest request, Class<?> type) throws Exception {
        if (!acceptsMultipartCreate()) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This resource accepts JSON only");
        }
        Object target = resolveNoArgConstructor(type, "multipart create model").newInstance();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(target);
        binder.setConversionService(conversionService);
        binder.bind(request);
        return target;
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

    /**
     * Binds, then validates ({@link #validate}) - unlike every other DTO this class reads off a
     * request (create/update/patch bodies), a bound {@code SearchDto} never went through the
     * {@link Validator} at all before this: a {@code @Max}/{@code @Min} the DTO author declared
     * on a filter or paging field was silently never enforced (Ground rules item 3).
     */
    private SearchDto bindSearchDto(Constructor<?> constructor, HttpServletRequest request) throws Exception {
        SearchDto searchDto = (SearchDto) constructor.newInstance();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(searchDto);
        binder.setConversionService(conversionService);
        binder.bind(request);
        bindSortDirectly(searchDto, request);
        validate(searchDto);
        return searchDto;
    }

    /**
     * Ground rules (backend-nunti integration) item 1: {@code ServletRequestDataBinder} hands a
     * single-occurrence {@code sort} query parameter to the conversion service as a plain {@code
     * String}, not a one-element array - converting that onto {@code AbstractSearchDto#sort}'s
     * {@code List<String>} then goes through {@code DefaultConversionService}'s {@code
     * StringToCollectionConverter}, which splits on <em>every</em> comma in the whole string. A
     * single {@code ?sort=lastName,asc} (the exact form {@code AbstractSearchDto}'s own javadoc
     * documents - and the one Spring Data's own {@code Pageable} resolver accepts) came out as
     * two list elements, {@code "lastName"} and {@code "asc"}, each parsed as its own property
     * with no direction, so {@code "asc"} failed "unknown sort property" before the direction
     * was ever read. A repeated {@code sort} param never hit this - Spring binds a multi-value
     * request parameter as a real array, one list element per occurrence, no comma-splitting
     * involved - which is why only the single-value form was broken.
     * <p>
     * Reads {@code request.getParameterValues("sort")} directly instead and overwrites whatever
     * {@link ServletRequestDataBinder} already (mis)bound - every value the client actually sent,
     * verbatim, whether there was one occurrence or many, with no comma-splitting ever applied to
     * it. {@code instanceof}, not an interface method: {@code sort} is {@link AbstractSearchDto}'s
     * own field, not part of the bare {@link SearchDto} contract, so a hand-rolled {@code
     * SearchDto} that doesn't extend it is unaffected.
     */
    private void bindSortDirectly(SearchDto searchDto, HttpServletRequest request) {
        if (!(searchDto instanceof AbstractSearchDto abstractSearchDto)) {
            return;
        }
        String[] rawSortValues = request.getParameterValues("sort");
        if (rawSortValues != null) {
            abstractSearchDto.setSort(new ArrayList<>(List.of(rawSortValues)));
        }
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

    /** {@link #DEFAULT_MAX_BULK_SIZE}/{@code restless.bulk.max-size}'s enforcement point - shared by {@link #createBulk}/{@link #updateBulk}/{@link #deleteAll}, called before any item is processed. */
    private void checkBulkSize(int size) {
        if (size > maxBulkSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bulk request carries " + size + " items, exceeding the maximum of " + maxBulkSize);
        }
    }

    private void validate(Object target) throws MethodArgumentNotValidException {
        validate(target, null);
    }

    /**
     * {@code group} (one of {@link RestlessValidationGroups}) is applied on top of {@code Default},
     * when the validator supports groups (Spring Boot's always does).
     */
    private void validate(Object target, Class<?> group) throws MethodArgumentNotValidException {
        BindingResult errors = violations(target, group);
        if (errors.hasErrors()) {
            throw new MethodArgumentNotValidException(new MethodParameter(VALIDATION_TARGET_METHOD, 0), errors);
        }
    }

    private BindingResult violations(Object target, Class<?> group) {
        BindingResult errors = new BeanPropertyBindingResult(target, target.getClass().getSimpleName());
        if (group != null && validator instanceof org.springframework.validation.SmartValidator smart) {
            smart.validate(target, errors, jakarta.validation.groups.Default.class, group);
        } else {
            validator.validate(target, errors);
        }
        return errors;
    }

    /** Marks an authorization denial inside a {@code PARTIAL} bulk row, so it aborts the request instead of becoming a row error. */
    private static final class BulkRowDenied extends RuntimeException {
        private final ResponseStatusException denial;

        BulkRowDenied(ResponseStatusException denial) {
            super(denial);
            this.denial = denial;
        }
    }

    /**
     * {@code PARTIAL} bulk create. Each row gets its own transaction ({@link
     * TransactionSupport#inNewTransaction}): a row that fails validation, that its data source
     * rejects, or that the database refuses is reported in {@link RestlessBulkResult#errors()} and
     * rolled back alone; a duplicate (see {@link #getBulkDeduplicator()}) is skipped. An
     * authorization denial is never "a bad row": it aborts the request, leaving the rows already
     * created - each of which passed the same checks - in place.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<?> createBulkPartial(HttpServletRequest request, RestlessContext ctx, List<Object> bodies,
                                                CreateDataSource rawDataSource) throws Exception {
        RestlessDedupeKey dedupe = getBulkDeduplicator();
        boolean dedupeOn = dedupe != null && !"false".equalsIgnoreCase(request.getParameter("dedupe"));
        Set<Object> seen = new HashSet<>();
        if (dedupeOn) {
            Specification<E> visible = withScope(withParentFilter(excludeSoftDeleted((root, query, cb) -> cb.conjunction()), ctx),
                    AuthorizationGuard.Action.READ_LIST, null, request);
            List<E> existing = transactionSupport.inReadOnlyTransaction(() ->
                    getReadDataSource().findAll(visible, org.springframework.data.domain.PageRequest.of(0, maxListSize), ctx).getContent());
            for (E row : existing) {
                Object key = dedupe.keyOfExisting(row);
                if (key != null) {
                    seen.add(key);
                }
            }
        }

        List<RestlessBulkResult.RowError> errors = new ArrayList<>();
        List<Object> items = new ArrayList<>();
        int skipped = 0;
        for (int index = 0; index < bodies.size(); index++) {
            Object body = bodies.get(index);
            BindingResult invalid = violations(body, RestlessValidationGroups.OnCreate.class);
            if (invalid.hasErrors()) {
                errors.add(new RestlessBulkResult.RowError(index, describe(invalid)));
                continue;
            }
            if (dedupeOn) {
                Object key = dedupe.keyOfModel(body);
                if (key != null && !seen.add(key)) {
                    skipped++;
                    continue;
                }
            }
            try {
                items.add(transactionSupport.inNewTransaction(() -> {
                    E created = (E) rawDataSource.create((CreateModel) body, ctx);
                    writeParentFieldIfNested(created, ctx);
                    fieldRules.applyPrincipal(created, ctx, conversionService);
                    try {
                        checkCanAccess(AuthorizationGuard.Action.CREATE, request, created);
                    } catch (ResponseStatusException denial) {
                        throw new BulkRowDenied(denial);
                    }
                    for (RestlessLifecycleHook<E> hook : hooks) {
                        hook.afterCreate(created, ctx);
                    }
                    rawDataSource.flush();
                    return getEntityMapper().map(created);
                }));
            } catch (BulkRowDenied denied) {
                throw denied.denial;
            } catch (ResponseStatusException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw e;
                }
                errors.add(new RestlessBulkResult.RowError(index, e.getReason() != null ? e.getReason() : e.getMessage()));
            } catch (MethodArgumentNotValidException e) {
                errors.add(new RestlessBulkResult.RowError(index, describe(e.getBindingResult())));
            } catch (RuntimeException e) {
                errors.add(new RestlessBulkResult.RowError(index, e instanceof org.springframework.dao.DataIntegrityViolationException
                        ? "The row conflicts with existing data" : String.valueOf(e.getMessage())));
            }
        }
        return ResponseEntity.ok(new RestlessBulkResult(items.size(), skipped, errors, items));
    }

    private static String describe(BindingResult errors) {
        return errors.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(java.util.stream.Collectors.joining("; "));
    }

    // ---- RestlessContext construction (Feature A) / nested-resource parent scoping (Feature B) ----

    /**
     * Builds this request's {@link RestlessContext} - every handler method calls this first,
     * before {@link #checkPreCheck} (so a guard's own {@code preCheck} can read it too via {@link
     * RestlessContext#current()}), then wraps its entire body in {@link RestlessContext#runWith}.
     */
    private RestlessContext buildContext(HttpServletRequest request, AuthorizationGuard.Action action, String customActionName) {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return RestlessContext.build(pathVariables == null ? Map.of() : pathVariables, request, action, customActionName,
                conversionService, parentDescriptor, principalIdResolver, fieldRules);
    }

    private boolean isNested() {
        return parentDescriptor != null;
    }

    /** {@code null} for a flat resource - the converted path value every nested-resource check below compares against. */
    private Object convertedParentValue(RestlessContext ctx) {
        return parentDescriptor == null ? null : ctx.pathVariable(parentDescriptor.pathVariable(), parentDescriptor.leafIdType());
    }

    /**
     * Feature B item 4: {@code true} whenever {@code entity}'s own parent value doesn't match the
     * path's - callers treat this exactly like "row doesn't exist" (a 404), never a distinct
     * status, so a caller scoped to the wrong parent can't tell "wrong parent" apart from "never
     * existed". Always {@code false} for a flat (non-nested) resource.
     */
    private boolean parentMismatch(E entity, RestlessContext ctx) {
        if (parentDescriptor == null || entity == null) {
            return false;
        }
        return !java.util.Objects.equals(ParentFieldSupport.readLeafValue(entity, parentDescriptor), convertedParentValue(ctx));
    }

    /**
     * Feature B item 2/5: forces {@code entity}'s own parent field to the path's value - called
     * right after create (so a body-supplied value for the same field is always overwritten,
     * never honored) and right after update/patch (so the parent field can never be moved by a
     * write - "protected like @Id"). A no-op for a flat resource.
     */
    private void writeParentFieldIfNested(E entity, RestlessContext ctx) {
        if (parentDescriptor == null) {
            return;
        }
        ParentFieldSupport.writeLeafValue(entity, parentDescriptor, convertedParentValue(ctx));
    }

    /** Feature B item 3: ANDs the parent-match predicate onto {@code spec} - a no-op for a flat resource. */
    private Specification<E> withParentFilter(Specification<E> spec, RestlessContext ctx) {
        if (parentDescriptor == null) {
            return spec;
        }
        return spec.and(ParentFieldSupport.<E>matches(parentDescriptor, convertedParentValue(ctx)));
    }

    /**
     * Feature B item 4 (bulk): any id missing from {@code found} (never existed, or exists under
     * a different parent) or present but failing {@link #parentMismatch} fails the <em>whole</em>
     * batch with a 404, before anything is written - called from inside the same transaction as
     * the write itself, same fail-fast-before-any-write spirit {@link #checkBulkSize} already
     * applies at the HTTP-shape level. A no-op for a flat resource.
     */
    private void checkAllFoundAndParentMatchedOrFail(Collection<K> requestedIds, List<E> found, RestlessContext ctx) {
        if (parentDescriptor == null) {
            return;
        }
        if (found.size() != requestedIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more entities not found");
        }
        for (E entity : found) {
            if (parentMismatch(entity, ctx)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more entities not found");
            }
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
        return cachedGuard != AuthorizationGuard.allowAll();
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

    // Denials and denyAs: under NOT_FOUND every denial below is shaped exactly like the matching
    // "doesn't exist" answer of the same route - same status, same message, and for the two plain
    // reads (findOne/namedView, whose missing row is a bodiless 404) no body either, see canRead.
    // A caller can't tell "not yours" from "not there". Metrics still count every denial.

    private boolean hidesDenials() {
        return denyAs == RestlessDenial.NOT_FOUND;
    }

    private void checkPreCheck(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request) {
        if (!cachedGuard.preCheck(action, customActionName, request)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "preCheck");
            throw hidesDenials()
                    ? new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found")
                    : new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to perform " + action);
        }
    }

    private void checkCanAccess(AuthorizationGuard.Action action, HttpServletRequest request, E entity) {
        if (!cachedGuard.canAccess(action, request, entity)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccess");
            throw denied(action, entity, "Not authorized to access this " + metadata.entityType().getSimpleName());
        }
    }

    /**
     * {@link #findOne}/{@link #namedView}: {@code false} for a denial under {@code NOT_FOUND}, so the
     * caller can answer with the same bodiless 404 a missing row gets; under {@code FORBIDDEN} a
     * denial throws the usual 403.
     */
    private boolean canRead(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request, E entity) {
        if (cachedGuard.canAccess(action, customActionName, request, entity)) {
            return true;
        }
        metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccess");
        if (hidesDenials()) {
            return false;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this " + metadata.entityType().getSimpleName());
    }

    /** Post-image counterpart to {@link #checkCanAccess} (Ground rules item 2) - see {@link AuthorizationGuard#canAccessAfterWrite}'s own javadoc. */
    private void checkCanAccessAfterWrite(AuthorizationGuard.Action action, HttpServletRequest request, E after) {
        if (!cachedGuard.canAccessAfterWrite(action, null, request, after)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccessAfterWrite");
            throw denied(action, after, "Not authorized to leave this " + metadata.entityType().getSimpleName() + " in its new state");
        }
    }

    /** Batched counterpart to {@link #checkCanAccess} (Ground rules Phase 2 item 10) - see {@link AuthorizationGuard#canAccessAll}'s own javadoc. */
    private void checkCanAccessAll(AuthorizationGuard.Action action, HttpServletRequest request, List<E> entities) {
        if (!cachedGuard.canAccessAll(action, request, entities)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccessAll");
            throw hidesDenials()
                    ? new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more entities not found")
                    : new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access one or more of these " + metadata.entityType().getSimpleName());
        }
    }

    /** Same as {@link #checkCanAccess(AuthorizationGuard.Action, HttpServletRequest, Object)}, threading a name through to the guard's own name-aware overload. */
    private void checkCanAccess(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request, E entity) {
        if (!cachedGuard.canAccess(action, customActionName, request, entity)) {
            metrics.recordDenial(metadata.entityType().getSimpleName(), action.name(), "canAccess");
            throw denied(action, entity, "Not authorized to access this " + metadata.entityType().getSimpleName());
        }
    }

    /**
     * The exception for a per-row denial: 403 with {@code forbiddenMessage}, or under {@code
     * NOT_FOUND} the exact "Entity {id} not found" every write route answers for a missing row. A
     * denied create gets a plain "Not found" - its id was only just generated and means nothing.
     */
    private ResponseStatusException denied(AuthorizationGuard.Action action, E entity, String forbiddenMessage) {
        if (!hidesDenials()) {
            return new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        return action == AuthorizationGuard.Action.CREATE
                ? new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found")
                : new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity " + idOf(entity) + " not found");
    }

    /**
     * Ground rules Phase 2 item 12 ("Fail-closed masking"): runs {@link AuthorizationGuard#postProcessResponse}
     * right after {@code Mapper.map(...)} on every single-entity response ({@link #create}/{@link
     * #findOne}/{@link #namedView}/{@link #update}/{@link #patch}) - a no-op for the default guard
     * and for any guard that doesn't override it, but the seam a {@code CerbosAuthorizationGuard}
     * needs to automatically mask {@code @CerbosHiddenField}-annotated DTO fields without every
     * hand-written {@code Mapper} needing to call {@code CerbosFieldMasker} itself. Not wired into
     * any list/page/bulk response - see the Changelog entry for this item.
     */
    private <D> D postProcessResponse(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request, E entity, D dto) {
        return cachedGuard.postProcessResponse(action, customActionName, request, entity, dto);
    }

    private Specification<E> withScope(Specification<E> spec, AuthorizationGuard.Action action, String customActionName, HttpServletRequest request) {
        Specification<E> scope = cachedGuard.scope(action, customActionName, request);
        return scope == null ? spec : spec.and(scope);
    }

    /**
     * ANDs in a {@code deleted = false} predicate for entities implementing {@link
     * SoftDeletable} - a no-op for every other entity. Applied to every list/page/custom-read
     * filter (see the three {@code getSpecification(...)}/{@code buildSpecification(...)} call
     * sites above), the same {@code .and(...)} composition idiom {@link #withScope} already uses
     * for authorization scoping. Deliberately <b>not</b> applied to {@link #findOne}/{@link
     * #namedView}/{@link #update}/{@link #patch} (all of which load by id via {@link
     * #getReadDataSource()} directly, bypassing {@code Specification} filtering entirely) - see
     * {@link #isSoftDeleted} for how each of those instead handles a soft-deleted row it loads.
     */
    private Specification<E> excludeSoftDeleted(Specification<E> spec) {
        if (!SoftDeletable.class.isAssignableFrom(metadata.entityType())) {
            return spec;
        }
        return spec.and((root, query, cb) -> cb.equal(root.get("deleted"), false));
    }

    /**
     * Ground rules Phase 2 item 13 ("Soft delete"): a write ({@link #update}/{@link #patch}/
     * {@link #deleteById}/{@link #writeAction}) on an already-soft-deleted row always 404s - as
     * far as a write is concerned, a soft-deleted row is gone, the same way a hard-deleted one
     * already is. {@link #findOne}/{@link #namedView} instead consult {@code
     * includeSoftDeletedInSingleRead} ({@code restless.soft-delete.include-in-single-read},
     * default {@code true} = today's pre-item-13 behavior: a soft-deleted row is still readable
     * by id) before deciding whether to 404 too.
     */
    private boolean isSoftDeleted(E entity) {
        return entity instanceof SoftDeletable softDeletable && softDeletable.isDeleted();
    }

    // ---- explicit transaction demarcation for the three bulk-write routes - see TransactionSupport ----

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
        // Ground rules item 8: a resource that disabled READ_LIST (see getEnabledOperations())
        // never registers a GET .../list route for itself, but @RestlessEmbed could still reach
        // its findEmbeddedList directly, bypassing that opt-out entirely - a caller who can't
        // list this resource on its own route shouldn't be able to via someone else's expand=.
        if (!getEnabledOperations().contains(AuthorizationGuard.Action.READ_LIST)) {
            return List.of();
        }
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_LIST, null);
        try {
            return RestlessContext.runWith(ctx, () -> {
                if (!cachedGuard.preCheck(AuthorizationGuard.Action.READ_LIST, null, request)) {
                    return List.of();
                }
                Specification<E> spec = withScope(withParentFilter(excludeSoftDeleted(joinFilter), ctx),
                        AuthorizationGuard.Action.READ_LIST, null, request);
                // Capped by maxListSize, same reasoning as findList's own cap: an embedded relation is
                // otherwise exactly as unbounded as GET .../list was before that cap existed, just with
                // no X-Restless-List-Truncated header to signal it (there's nowhere to put one on a field
                // nested inside another resource's response).
                List<E> capped = getReadDataSource().findAll(spec, org.springframework.data.domain.PageRequest.of(0, maxListSize), ctx).getContent();
                return getEntityMapper().map(capped);
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Callable#call() declares `throws Exception`, but nothing reachable from this
            // particular body actually throws a checked one - this method's own signature can't
            // grow a `throws Exception` of its own without breaking RestlessEmbedResolver's call
            // site, a different package entirely.
            throw new IllegalStateException("Unexpected checked exception resolving an embedded list", e);
        }
    }

    /**
     * Same as {@link #findEmbeddedList}, for a {@code many = false} {@link RestlessEmbed} field:
     * {@code joinFilter} is expected to match at most one row (a natural-key equality check, the
     * same assumption every other natural-key "join" in this codebase already makes). {@code
     * null} (not 404/403) for no match, a disabled {@code READ_ONE} operation, a denied {@code
     * preCheck}, or a denied {@code canAccess} on the row that did match.
     * <p>
     * Ground rules item 8: queries with a limit of 2, not 1 - more than one match means {@code
     * sourceField}/{@code targetField} don't actually form the natural key this annotation
     * assumes, which is a configuration error in the entity author's own {@code @RestlessEmbed}
     * declaration, not a routine "which one do I show" runtime decision. Taking an arbitrary
     * first row would silently hide that mistake instead of surfacing it.
     */
    public final Object findEmbeddedOne(Specification<E> joinFilter, HttpServletRequest request) {
        if (!getEnabledOperations().contains(AuthorizationGuard.Action.READ_ONE)) {
            return null;
        }
        RestlessContext ctx = buildContext(request, AuthorizationGuard.Action.READ_ONE, null);
        try {
            return RestlessContext.runWith(ctx, () -> {
                if (!cachedGuard.preCheck(AuthorizationGuard.Action.READ_ONE, null, request)) {
                    return null;
                }
                Specification<E> spec = withScope(withParentFilter(excludeSoftDeleted(joinFilter), ctx),
                        AuthorizationGuard.Action.READ_ONE, null, request);
                List<E> matches = getReadDataSource().findAll(spec, org.springframework.data.domain.PageRequest.of(0, 2), ctx).getContent();
                if (matches.isEmpty()) {
                    return null;
                }
                if (matches.size() > 1) {
                    throw new IllegalStateException("@RestlessEmbed join matched more than one "
                            + metadata.entityType().getSimpleName() + " row - sourceField/targetField don't form a natural key");
                }
                E found = matches.get(0);
                if (!cachedGuard.canAccess(AuthorizationGuard.Action.READ_ONE, request, found)) {
                    return null;
                }
                return getEntityMapper().map(found);
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Same reasoning as findEmbeddedList's own catch - nothing reachable here actually
            // throws a checked exception, and this method's signature can't grow one either.
            throw new IllegalStateException("Unexpected checked exception resolving an embedded value", e);
        }
    }
}
