package ro.cristivoicu.springbootrestless.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Records an authorization-denial count per resource/action/hook - the one signal generic HTTP
 * metrics (Spring Boot's own {@code http.server.requests}, already instrumenting every {@code
 * RestlessRegistrar}-registered route for free the moment Actuator/Micrometer are on the
 * classpath, since those routes are ordinary {@code RequestMappingInfo} entries like any other)
 * can't provide: <em>why</em> a 403 happened, not just that one did. {@code @ConditionalOnClass}
 * on {@link MeterRegistry} (Micrometer, riding in transitively with {@code
 * spring-boot-starter-actuator} - an {@code optional} dependency of {@code app}, same reasoning
 * as {@code cerbos}'s own {@code CerbosHealthIndicator}) so a consumer with neither on their
 * classpath gets no bean at all, not a {@code ClassNotFoundException}.
 * <p>
 * {@link #NONE} is the no-op every {@code RestlessResourceHandler} defaults to when no bean of
 * this type is available (Micrometer absent, or a resource constructed by hand outside Spring) -
 * every method a plain no-op, so callers never need a null check.
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
public class RestlessAuthorizationMetrics {

    public static final RestlessAuthorizationMetrics NONE = new RestlessAuthorizationMetrics();

    private final MeterRegistry registry;

    private RestlessAuthorizationMetrics() {
        this.registry = null;
    }

    /**
     * {@code @Autowired} is load-bearing, not decorative: this class deliberately has two
     * constructors (the private no-arg one above, only ever used for {@link #NONE}), and without
     * an explicit marker Spring's constructor resolution silently prefers a no-arg constructor
     * over an unmarked one taking arguments - it would otherwise construct the Spring-managed bean
     * via the <em>private</em> constructor too, leaving {@link #registry} {@code null} on what's
     * supposed to be the real, registry-backed instance (confirmed the hard way: a denial counter
     * that silently stayed at zero because of exactly this).
     */
    @Autowired
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
