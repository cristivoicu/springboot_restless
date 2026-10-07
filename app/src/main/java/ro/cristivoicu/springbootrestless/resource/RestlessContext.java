package ro.cristivoicu.springbootrestless.resource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

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

    private RestlessContext(Map<String, String> pathVariables, HttpServletRequest request, Object authentication,
                             AuthorizationGuard.Action action, String customActionName, ConversionService conversionService) {
        this.pathVariables = pathVariables;
        this.request = request;
        this.authentication = authentication;
        this.action = action;
        this.customActionName = customActionName;
        this.conversionService = conversionService;
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

    static RestlessContext build(Map<String, String> pathVariables, HttpServletRequest request,
                                  AuthorizationGuard.Action action, String customActionName,
                                  ConversionService conversionService) {
        return new RestlessContext(pathVariables, request, OptionalSpringSecurity.currentAuthentication(),
                action, customActionName, conversionService);
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
