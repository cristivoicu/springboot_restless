package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosException;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports whether the Cerbos PDP {@link CerbosBlockingClient} talks to is actually reachable -
 * without this, "is authorization working" is only ever discoverable by hitting a guarded route
 * and seeing whether {@link CerbosAuthorizationGuard} fails closed (see its javadoc), which is a
 * bad way to find out your PDP has been down for an hour.
 * <p>
 * The check itself is a coarse {@code check()} call against a synthetic resource kind/action that
 * almost certainly has no matching policy - same "attribute-less, id-less placeholder" idiom
 * {@link CerbosAuthorizationGuard#preCheck} already uses for its own pre-load resource. The
 * *decision* (allow or deny) is irrelevant here and deliberately ignored; only whether the RPC
 * completed at all is - Cerbos answers "not allowed" for an unrecognized resource kind just as
 * readily as for a recognized one denying access, so this never reports {@code UP} based on a
 * false positive.
 * <p>
 * Registered via {@code CerbosAutoConfiguration}'s {@code @ConditionalOnClass(HealthIndicator.class)}
 * bean method: only activates when the consumer's own app has actuator on its classpath (this
 * module's own dependency on it is {@code optional=true} - see its {@code pom.xml}), so a
 * consumer who never asked for actuator never gets a surprise health contributor.
 */
public class CerbosHealthIndicator implements HealthIndicator {

    private static final String HEALTH_CHECK_RESOURCE_KIND = "spring_boot_restless_health_check";

    private final CerbosBlockingClient client;

    public CerbosHealthIndicator(CerbosBlockingClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            // Cerbos validates the request shape before evaluating it - a principal needs at
            // least one role, even a meaningless one, or check() itself rejects the request with
            // INVALID_ARGUMENT before ever reaching policy evaluation (which would otherwise get
            // misread here as the PDP being unreachable, when it isn't).
            client.check(Principal.newInstance("health-check", HEALTH_CHECK_RESOURCE_KIND),
                    Resource.newInstance(HEALTH_CHECK_RESOURCE_KIND, "health-check"),
                    HEALTH_CHECK_RESOURCE_KIND);
            return Health.up().build();
        } catch (CerbosException e) {
            return Health.down(e)
                    .withDetail("cerbosStatusCode", e.getStatusCode())
                    .withDetail("cerbosStatusDescription", e.getStatusDescription())
                    .build();
        }
    }
}
