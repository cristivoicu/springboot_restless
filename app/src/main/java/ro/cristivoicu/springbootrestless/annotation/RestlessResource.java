package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code RestlessResourceHandler} subclass for automatic HTTP route registration by
 * {@code RestlessRegistrar}. Lives on the resource-descriptor bean's class — never on the JPA
 * entity itself — so REST concerns stay out of the domain model.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessResource {

    /**
     * The base path this resource is mounted at, e.g. {@code "/employees"}.
     */
    String basePath();

    /**
     * This resource's API version, in the same syntax {@code @RequestMapping(version = ...)}
     * accepts (Spring Framework's own API versioning support - plain {@code "1.0"}/{@code "2"},
     * or a baseline-or-higher range like {@code "1.5+"}, parsed by whichever {@code
     * ApiVersionParser} the app configured). Every route this resource registers carries it, via
     * {@code RequestMappingInfo.Builder.version(...)}.
     * <p>
     * Left unset (the default), a route carries no version constraint at all - reachable
     * regardless of what version a request resolves to - exactly like an {@code @RequestMapping}
     * with no {@code version} attribute.
     * <p>
     * This only declares <em>which</em> version a resource's routes belong to; it doesn't decide
     * <em>how</em> a request's version is resolved (header, path segment, query param, media type
     * parameter) - that's an app-wide concern, configured once via {@code ApiVersionConfigurer}
     * in a {@code WebMvcConfigurer} (see Spring's own API versioning documentation), same as any
     * hand-written {@code @RequestMapping(version = ...)} controller method needs.
     * <p>
     * <b>{@code ApiVersionConfigurer.detectSupportedVersions(true)} won't see this version.</b>
     * That scan runs during {@code RequestMappingHandlerMapping}'s own startup detection of
     * ordinary {@code @RequestMapping} beans - before {@code RestlessRegistrar}'s {@code
     * SmartInitializingSingleton} has registered anything dynamically. List a resource's declared
     * versions explicitly via {@code addSupportedVersions(...)} instead, or every request
     * carrying one gets rejected with {@code InvalidApiVersionException} even when it matches a
     * real route.
     */
    String version() default "";

    /**
     * Explicit, auditable opt-out of authorization entirely. {@code RestlessRegistrar} refuses to
     * register a resource that has no {@link ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard}
     * override (i.e. still {@code AuthorizationGuard.allowAll()}) unless this is {@code true} —
     * a fail-fast startup check, not a runtime behavior change: a resource that already overrides
     * {@code getAuthorizationGuard()} is completely unaffected by this attribute either way.
     * <p>
     * Defaults to {@code false} specifically so forgetting to wire a guard on a resource that was
     * meant to have one is a startup-time {@code IllegalStateException} naming the resource, not
     * a silently wide-open route discovered later. Set {@code true} only for a resource that is
     * genuinely meant to be unauthenticated/unauthorized on purpose (a public read-only lookup
     * table, a demo fixture) — the annotation then documents that decision at the call site
     * instead of leaving it indistinguishable from "nobody got around to it yet".
     */
    boolean allowAll() default false;
}
