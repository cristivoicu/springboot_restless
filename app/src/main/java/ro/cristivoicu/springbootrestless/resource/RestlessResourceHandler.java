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
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.patch.PatchDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.models.DeleteModel;
import ro.cristivoicu.springbootrestless.models.PageableResponse;
import ro.cristivoicu.springbootrestless.models.PatchModel;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Wires this resource's infra collaborators. Called once by whichever registrar discovered
     * this bean (a hardcoded call in Stage 1, {@code RestlessRegistrar} from Stage 2 on) — kept
     * separate from the constructor so the entity author's subclass stays free of infra plumbing.
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator) {
        this.metadata = metadata;
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.validator = validator;
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
     * Resolves this resource's entity/id/DTO {@link Class} tokens via reflection off the
     * concrete subclass's own generics — {@code E}/{@code K} from this class's superclass
     * type arguments, each DTO type from the corresponding {@code *DataSource} subclass's
     * own generics. Called once by {@code RestlessRegistrar} at startup; kept here (not in
     * the registrar) because only code inside this class can call the protected
     * {@code getXDataSource()} accessors.
     */
    public final ResourceMetadata resolveMetadata(String basePath) {
        Class<?>[] entityAndId = GenericTypeResolver.resolveTypeArguments(getClass(), RestlessResourceHandler.class);
        if (entityAndId == null) {
            throw new IllegalStateException(getClass() + " must extend RestlessResourceHandler<E, K> with concrete type arguments");
        }

        Class<?> createModelType = resolveDtoType(getCreateDataSource(), CreateDataSource.class);
        Class<?> updateModelType = resolveDtoType(getUpdateDataSource(), UpdateDataSource.class);
        Class<?> deleteModelType = resolveDtoType(getDeleteDataSource(), DeleteDataSource.class);
        Class<?> searchDtoType = resolveDtoType(getReadDataSource(), ReadDataSource.class);

        return new ResourceMetadata(basePath, entityAndId[0], entityAndId[1],
                createModelType, updateModelType, deleteModelType, searchDtoType);
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
     * paging fields), ANDed together. Covers the common "filter by whichever fields were
     * populated" case; override for anything a plain equality match can't express (ranges,
     * joins, {@code LIKE}, ...).
     * <p>
     * Primitive fields (e.g. {@code boolean}) are skipped entirely, not just when zero-valued:
     * a primitive can never represent "the client didn't send this filter" (Java always defaults
     * it, e.g. {@code false}), so treating an unset primitive field as an explicit filter would
     * silently exclude every non-default row from unfiltered searches. Use a boxed type
     * ({@code Boolean}) for an optional equality filter instead.
     */
    protected Specification<E> getSpecification(SearchDto searchDto) {
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
                predicates.add(cb.equal(root.get(field.getName()), value));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
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
        return ResponseEntity.ok(getEntityMapper().map(created));
    }

    /**
     * Bulk create - {@code POST {basePath}/bulk}, body a JSON array of {@code CreateModel}s.
     * {@link #checkPreCheck} is one coarse "can this principal create at all" call, same as
     * single {@link #create} - there's no per-item {@code canAccess} the way bulk update/delete
     * have, since none of these rows exist yet for a per-instance check to run against. Every
     * item is validated before any of them are created (fail-fast, same spirit as {@link
     * #deleteAll}'s per-id guard check running entirely before the first delete).
     */
    public final ResponseEntity<?> createBulk(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.CREATE, null, request);
        List<Object> bodies = readBodyList(request, metadata.createModelType());
        for (Object body : bodies) {
            validate(body);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        CreateDataSource rawDataSource = getCreateDataSource();
        @SuppressWarnings("unchecked")
        List<E> created = rawDataSource.createAll(bodies);
        return ResponseEntity.ok(getEntityMapper().map(created));
    }

    public final ResponseEntity<?> findOne(HttpServletRequest request) {
        checkPreCheck(AuthorizationGuard.Action.READ_ONE, null, request);
        K id = extractId(request);
        E found = getReadDataSource().findOne(id);
        if (found == null) {
            return ResponseEntity.notFound().build();
        }
        checkCanAccess(AuthorizationGuard.Action.READ_ONE, request, found);
        return ResponseEntity.ok(getEntityMapper().map(found));
    }

    public final ResponseEntity<List<?>> findList(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.READ_LIST, null, request);
        SearchDto searchDto = bindSearchDto(searchDtoConstructor, request);
        Specification<E> spec = withScope(getSpecification(searchDto), AuthorizationGuard.Action.READ_LIST, null, request);
        List<E> data = getReadDataSource().findAll(spec);
        return ResponseEntity.ok(getOverviewMapper().map(data));
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
        spec = withScope(spec, AuthorizationGuard.Action.CUSTOM_READ, actionName, request);

        return ResponseEntity.ok(paginate(getOverviewMapper(), spec, pageableOf(searchDto)));
    }

    public final ResponseEntity<?> update(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.UPDATE, null, request);
        K id = extractId(request);
        // Loaded purely for the guard check - UpdateDataSource.update() loads/mutates/saves as
        // one atomic unit and never hands the entity back to us beforehand. Skipped entirely
        // (not just short-circuited on a denial) when no guard is configured, so resources that
        // never opted into authorization don't pay for an extra SELECT on every write.
        if (hasGuard()) {
            E existing = getReadDataSource().findOne(id);
            if (existing != null) {
                checkCanAccess(AuthorizationGuard.Action.UPDATE, request, existing);
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
        if (hasGuard()) {
            E existing = getReadDataSource().findOne(id);
            if (existing != null) {
                checkCanAccess(AuthorizationGuard.Action.PATCH, request, existing);
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
     * turned out to be denied.
     */
    public final ResponseEntity<?> updateBulk(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.UPDATE, null, request);
        Map<String, Object> rawBodies = readBodyMap(request, metadata.updateModelType());

        Map<K, Object> byId = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawBodies.entrySet()) {
            validate(entry.getValue());
            byId.put(convertId(entry.getKey()), entry.getValue());
        }
        if (hasGuard()) {
            for (K id : byId.keySet()) {
                E existing = getReadDataSource().findOne(id);
                if (existing != null) {
                    checkCanAccess(AuthorizationGuard.Action.UPDATE, request, existing);
                }
            }
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        UpdateDataSource rawDataSource = getUpdateDataSource();
        @SuppressWarnings("unchecked")
        List<E> updated = rawDataSource.updateAll(byId);
        return ResponseEntity.ok(getEntityMapper().map(updated));
    }

    public final ResponseEntity<?> deleteById(HttpServletRequest request) {
        checkPreCheck(AuthorizationGuard.Action.DELETE_ONE, null, request);
        K id = extractId(request);
        // Same reasoning as update(): loaded purely for the guard check, skipped entirely when
        // no guard is configured; not found falls through unchanged to DeleteDataSource's own
        // (today: silent) not-found behavior.
        if (hasGuard()) {
            E existing = getReadDataSource().findOne(id);
            if (existing != null) {
                checkCanAccess(AuthorizationGuard.Action.DELETE_ONE, request, existing);
            }
        }
        getDeleteDataSource().deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public final ResponseEntity<?> deleteAll(HttpServletRequest request) throws Exception {
        checkPreCheck(AuthorizationGuard.Action.DELETE_ALL, null, request);
        Object body = readBody(request, metadata.deleteModelType());
        DeleteModel deleteModel = (DeleteModel) body;
        // Fail-fast, before deleting anything: check every targeted entity up front so a bulk
        // delete never partially completes before hitting a denied id. Skipped entirely (the
        // whole loop, not just the check) when no guard is configured.
        if (hasGuard()) {
            for (String rawId : deleteModel.getIds()) {
                E existing = getReadDataSource().findOne(convertId(rawId));
                if (existing != null) {
                    checkCanAccess(AuthorizationGuard.Action.DELETE_ALL, request, existing);
                }
            }
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        DeleteDataSource rawDataSource = getDeleteDataSource();
        rawDataSource.deleteAll(deleteModel);
        return ResponseEntity.noContent().build();
    }

    // ---- shared per-request parsing, reusing Spring's own infra rather than hand-rolling it ----

    private PageableResponse<List<?>> paginate(HttpServletRequest request, Mapper<E, ?> mapper, AuthorizationGuard.Action action) throws Exception {
        SearchDto searchDto = bindSearchDto(searchDtoConstructor, request);
        Specification<E> spec = withScope(getSpecification(searchDto), action, null, request);
        return paginate(mapper, spec, pageableOf(searchDto));
    }

    /**
     * Shared by {@link #findPage}/{@link #findPageOverview}/{@link #findPageSelect} (via the
     * request-binding overload above, using {@link #getSpecification}) and {@link #customRead}
     * (using a {@link ReadAction}'s own specification) — pagination/response-shaping logic is
     * identical either way, only the filter source and mapper differ.
     */
    private PageableResponse<List<?>> paginate(Mapper<E, ?> mapper, Specification<E> spec, Pageable pageable) {
        Page<E> page = getReadDataSource().findAll(spec, pageable);

        PageableResponse<List<?>> response = new PageableResponse<>();
        response.setPageSize(page.getSize());
        response.setTotalPages(page.getTotalPages());
        response.setTotalElements(page.getTotalElements());
        response.setBody(mapper.map(page.getContent()));
        return response;
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

        Set<String> entityProperties = Arrays.stream(metadata.entityType().getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        for (Sort.Order order : pageable.getSort()) {
            if (!entityProperties.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown sort property '" + order.getProperty() + "' for " + metadata.entityType().getSimpleName());
            }
        }
        return pageable;
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

    private void checkPreCheck(AuthorizationGuard.Action action, String customActionName, HttpServletRequest request) {
        if (!getAuthorizationGuard().preCheck(action, customActionName, request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to perform " + action);
        }
    }

    private void checkCanAccess(AuthorizationGuard.Action action, HttpServletRequest request, E entity) {
        if (!getAuthorizationGuard().canAccess(action, request, entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to access this " + metadata.entityType().getSimpleName());
        }
    }

    private Specification<E> withScope(Specification<E> spec, AuthorizationGuard.Action action, String customActionName, HttpServletRequest request) {
        Specification<E> scope = getAuthorizationGuard().scope(action, customActionName, request);
        return scope == null ? spec : spec.and(scope);
    }
}
