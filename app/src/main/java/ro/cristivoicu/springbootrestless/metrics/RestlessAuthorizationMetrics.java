package ro.cristivoicu.springbootrestless.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Records an authorization-denial count per resource/action/hook - the one signal generic HTTP
 * metrics (Spring Boot's own {@code http.server.requests}, already instrumenting every {@code
 * RestlessRegistrar}-registered route for free the moment Actuator/Micrometer are on the
 * classpath, since those routes are ordinary {@code RequestMappingInfo} entries like any other)
 * can't provide: <em>why</em> a 403 happened, not just that one did. Registered via {@code
 * RestlessAutoConfiguration}'s {@code @ConditionalOnClass(MeterRegistry.class)} bean method
 * (Micrometer, riding in transitively with {@code spring-boot-starter-actuator} - an {@code
 * optional} dependency of {@code app}, same reasoning as {@code cerbos}'s own {@code
 * CerbosHealthIndicator}) so a consumer with neither on their classpath gets no bean at all, not a
 * {@code ClassNotFoundException}.
 * <p>
 * {@link #NONE} is the no-op every {@code RestlessResourceHandler} defaults to when no bean of
 * this type is available (Micrometer absent, or a resource constructed by hand outside Spring) -
 * every method a plain no-op, so callers never need a null check.
 */
public class RestlessAuthorizationMetrics {

    public static final RestlessAuthorizationMetrics NONE = new RestlessAuthorizationMetrics();

    private final MeterRegistry registry;

    private RestlessAuthorizationMetrics() {
        this.registry = null;
    }

    /**
     * The constructor {@code RestlessAutoConfiguration}'s {@code @Bean} method calls explicitly
     * (passing the real {@code ObjectProvider<MeterRegistry>} Spring resolves for it) - since
     * this class is no longer {@code @Component}-scanned, there's no ambiguous-constructor
     * resolution to guard against the way there used to be; the private no-arg constructor above
     * is only ever reached via {@link #NONE}'s own direct {@code new}.
     */
    public RestlessAuthorizationMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /** {@code resource}: the entity's simple name. {@code action}: an {@code AuthorizationGuard.Action} name. {@code hook}: {@code "preCheck"} or {@code "canAccess"} - which of the two denied it. */
    public void recordDenial(String resource, String action, String hook) {
        if (registry == null) {
            return;
        }
        Counter.builder("restless.authorization.denials")
                .description("Count of AuthorizationGuard denials, by resource/action/hook")
                .tag("resource", resource)
                .tag("action", action)
                .tag("hook", hook)
                .register(registry)
                .increment();
    }
}
