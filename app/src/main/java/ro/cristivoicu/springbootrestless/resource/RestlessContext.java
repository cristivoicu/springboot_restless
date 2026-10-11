package ro.cristivoicu.springbootrestless.resource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.authorization.RestlessPrincipalIdResolver;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Per-request context available wherever request-dependent logic lives - data sources (via the
 * {@code *(..., RestlessContext)} overloads every {@code *DataSource} now carries, see e.g.
 * {@link ro.cristivoicu.springbootrestless.controller.create.CreateDataSource#create(Object,
 * RestlessContext)}) and guards (via {@link #current()}, since {@link AuthorizationGuard}'s own
 * hook methods can't grow a new parameter without breaking every existing implementation).
 * <p>
 * Built once per request by {@code RestlessResourceHandler}, immediately before {@code
 * checkPreCheck} runs - so a guard's own {@code preCheck} can read it too (see nested-resource
 * parent authorization: a guard can resolve {@code pathVariable("eventId", ...)} there and check
 * it once per request, rather than per row) - and installed into a thread-local via {@link
 * #runWith} for the duration of that one handler method, restoring whatever {@link #current()}
 * was before once it returns or throws. That "restore, don't just clear" semantic matters for a
 * nested call into a <em>different</em> resource's own handler methods - see {@code
 * RestlessEmbed} resolution, which calls back into one resource's {@code findEmbeddedList}/
 * {@code findEmbeddedOne} from inside another resource's own request.
 * <p>
 * {@link #authentication()} is deliberately typed {@code Object}, not {@code
 * org.springframework.security.core.Authentication} - {@code app} has no mandatory Spring
 * Security dependency (see its {@code pom.xml}), and unlike a {@code @ConditionalOnClass}-gated
 * bean, this class is built on every single request regardless of whether Spring Security is
 * even on the classpath, so its own public method signatures can never directly reference a type
 * that might not resolve there. Cast it yourself - {@code (Authentication) ctx.authentication()}
 * - on an app that actually has Spring Security.
 */
public final class RestlessContext {

    private static final ThreadLocal<RestlessContext> HOLDER = new ThreadLocal<>();

    private final Map<String, String> pathVariables;
    private final HttpServletRequest request;
    private final Object authentication;
    private final AuthorizationGuard.Action action;
    private final String customActionName;
    private final ConversionService conversionService;
    // Null for a flat (non-nested) resource. Lets applyParentTo write the path's parent id the same
    // way RestlessResourceHandler does, from inside a data source, before the entity is saved.
    private final ParentFieldSupport.ParentDescriptor parent;
    private final RestlessPrincipalIdResolver principalIdResolver;
    private final FieldRules fieldRules;
    // Resolved on first use, at most once per request: a resolver may well hit the database.
    private boolean principalResolved;
    private Object principalId;

    private RestlessContext(Map<String, String> pathVariables, HttpServletRequest request, Object authentication,
                             AuthorizationGuard.Action action, String customActionName, ConversionService conversionService,
                             ParentFieldSupport.ParentDescriptor parent, RestlessPrincipalIdResolver principalIdResolver,
                             FieldRules fieldRules) {
        this.pathVariables = pathVariables;
        this.request = request;
        this.authentication = authentication;
        this.action = action;
        this.customActionName = customActionName;
        this.conversionService = conversionService;
        this.parent = parent;
        this.principalIdResolver = principalIdResolver == null ? RestlessPrincipalIdResolver.AUTHENTICATION_NAME : principalIdResolver;
        this.fieldRules = fieldRules == null ? FieldRules.NONE : fieldRules;
    }

    /**
     * The context for whichever request is currently executing on this thread, or {@code null}
     * outside of one entirely (e.g. a background job, or a test calling a data source directly
     * with no handler method anywhere in the call stack).
     */
    public static RestlessContext current() {
        return HOLDER.get();
    }

    /**
     * Every path variable Spring MVC resolved for the route that matched this request, raw
     * (unconverted) - empty for a route with none.
     */
    public Map<String, String> pathVariables() {
        return pathVariables;
    }

    /**
     * {@code pathVariables().get(name)}, converted to {@code type} via the app's own real {@link
     * ConversionService} (so a consumer-registered custom {@code Converter} is actually
     * consulted, same reasoning {@code DefaultDeleteDataSource}'s own bulk-delete id conversion
     * already applies) - {@code null} if {@code name} wasn't matched at all by the route, a clean
     * {@code 400} (not an unhandled {@link ConversionException}) if it was but couldn't convert.
     */
    public <T> T pathVariable(String name, Class<T> type) {
        String raw = pathVariables.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return conversionService.convert(raw, type);
        } catch (ConversionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to convert path variable '" + name + "' to " + type.getSimpleName(), e);
        }
    }

    public HttpServletRequest request() {
        return request;
    }

    /**
     * See this class's own javadoc for why this is {@code Object}, not {@code Authentication} -
     * {@code null} whenever Spring Security isn't on the classpath, or nothing is authenticated.
     */
    public Object authentication() {
        return authentication;
    }

    /**
     * Which {@link AuthorizationGuard.Action} this request is being checked against - the same
     * value {@code checkPreCheck}/{@code checkCanAccess} pass to the guard for this same request.
     */
    public AuthorizationGuard.Action action() {
        return action;
    }

    /** The custom read/write-action or named-view name, for an action that carries one - {@code null} for every fixed CRUD/bulk route. */
    public String customActionName() {
        return customActionName;
    }

    /**
     * The current principal's id, from the application's {@link RestlessPrincipalIdResolver} bean
     * (default: the authenticated principal's name) - {@code null} when there is none. Resolved
     * once per request and cached.
     */
    public Object principalId() {
        if (!principalResolved) {
            principalId = principalIdResolver.resolve(this);
            principalResolved = true;
        }
        return principalId;
    }

    /**
     * {@link #principalId()} converted to {@code type} with the application's {@link
     * ConversionService} - {@code null} when there is no principal, or when it can't be converted
     * (a principal that doesn't fit an id column owns nothing; callers treat {@code null} as a
     * denial).
     */
    public <T> T principalId(Class<T> type) {
        Object raw = principalId();
        if (raw == null) {
            return null;
        }
        try {
            return conversionService.convert(raw, type);
        } catch (ConversionException e) {
            return null;
        }
    }

    /** The {@code @RestlessParent} field path of a nested resource ({@code "eventId"}, {@code "project.id"}), or {@code null} for a flat one. */
    public String parentField() {
        return parent == null ? null : String.join(".", parent.segments().stream().map(java.lang.reflect.Field::getName).toList());
    }

    /** The parent id from the path of a nested resource, converted to the parent id type - {@code null} for a flat resource. */
    public Object parentValue() {
        return parent == null ? null : pathVariable(parent.pathVariable(), parent.leafIdType());
    }

    /**
     * Writes every server-managed field a create needs before the entity is saved: the nested
     * resource's parent ({@link #applyParentTo}) and every {@code @RestlessFromPrincipal} field.
     * {@code DefaultCreateDataSource} calls it for you; a hand-written create data source that
     * builds and saves its own entity should call it before {@code save}, for the same NOT NULL
     * reason {@link #applyParentTo} documents. The handler writes both again after any data source,
     * so skipping it is never a security hole - only, possibly, a constraint violation.
     *
     * @throws ResponseStatusException 401 if the entity has a principal field and there is no principal
     */
    public void applyServerFieldsTo(Object entity) {
        applyParentTo(entity);
        fieldRules.applyPrincipal(entity, this, conversionService);
    }

    /**
     * Nested resources: writes the parent id from the path onto {@code entity}'s
     * {@code @RestlessParent} field - a no-op for a flat resource. A create data source calls this
     * <em>before</em> saving: {@code RestlessResourceHandler} also writes the parent after
     * {@code create(...)} returns (so a data source that never calls this is still correct), but
     * by then the entity has already been persisted, and Hibernate builds the INSERT from the state
     * at persist time - the late value only reaches the database as a follow-up UPDATE. A NOT NULL
     * parent column (the normal case for a mandatory parent) rejects that INSERT outright.
     * {@code DefaultCreateDataSource} calls it for you.
     *
     * @throws ResponseStatusException 400 if the path value doesn't convert to the parent id type
     */
    public void applyParentTo(Object entity) {
        if (parent == null || entity == null) {
            return;
        }
        ParentFieldSupport.writeLeafValue(entity, parent, pathVariable(parent.pathVariable(), parent.leafIdType()));
    }

    static RestlessContext build(Map<String, String> pathVariables, HttpServletRequest request,
                                  AuthorizationGuard.Action action, String customActionName,
                                  ConversionService conversionService) {
        return build(pathVariables, request, action, customActionName, conversionService, null);
    }

    static RestlessContext build(Map<String, String> pathVariables, HttpServletRequest request,
                                  AuthorizationGuard.Action action, String customActionName,
                                  ConversionService conversionService, ParentFieldSupport.ParentDescriptor parent) {
        return build(pathVariables, request, action, customActionName, conversionService, parent, null, null);
    }

    static RestlessContext build(Map<String, String> pathVariables, HttpServletRequest request,
                                  AuthorizationGuard.Action action, String customActionName,
                                  ConversionService conversionService, ParentFieldSupport.ParentDescriptor parent,
                                  RestlessPrincipalIdResolver principalIdResolver, FieldRules fieldRules) {
        return new RestlessContext(pathVariables, request, OptionalSpringSecurity.currentAuthentication(),
                action, customActionName, conversionService, parent, principalIdResolver, fieldRules);
    }

    /**
     * Installs {@code ctx} as {@link #current()} for the duration of {@code body}, restoring
     * whatever was there before (not necessarily {@code null} - see this class's own javadoc for
     * the nested, cross-resource call case) once it returns or throws.
     */
    static <T> T runWith(RestlessContext ctx, Callable<T> body) throws Exception {
        RestlessContext previous = HOLDER.get();
        HOLDER.set(ctx);
        try {
            return body.call();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
