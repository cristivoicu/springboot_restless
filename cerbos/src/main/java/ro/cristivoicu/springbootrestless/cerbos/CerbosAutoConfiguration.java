package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Wires every infrastructure bean this module needs, discovered automatically by {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} the moment
 * {@code spring-boot-restless-cerbos} is on the classpath - no consumer {@code @Import}/{@code
 * @ComponentScan} needed, same fix {@code RestlessAutoConfiguration} (the {@code app} module)
 * applies for the core framework's own beans. Deliberately still keeps every authentication
 * concern (JWT decoding, {@code SecurityFilterChain}, ...) out of its own hands - that stays the
 * consumer's own configuration, per this module's design.
 */
@AutoConfiguration
public class CerbosAutoConfiguration {

    /**
     * A consumer only needs {@code cerbos.client.target} pointed at a running PDP (a real
     * deployment, or a {@code dev.cerbos.sdk.CerbosContainer} in tests) to get a usable client.
     * {@code cerbos.client.timeout} sets the gRPC deadline {@link CerbosBlockingClient} applies to
     * every call (the SDK defaults to 1s if never configured at all; explicit here so it's a
     * visible, tunable property rather than an implicit SDK default) - this is what bounds how
     * long a slow or unreachable PDP can hold up a guarded request before {@link
     * CerbosAuthorizationGuard} gets a chance to fail closed - see its own javadoc.
     */
    @Bean
    @ConditionalOnMissingBean
    public CerbosBlockingClient cerbosBlockingClient(
            @Value("${cerbos.client.target:localhost:3593}") String target,
            @Value("${cerbos.client.plaintext:true}") boolean plaintext,
            @Value("${cerbos.client.timeout:3s}") Duration timeout) throws CerbosClientBuilder.InvalidClientConfigurationException {
        CerbosClientBuilder builder = new CerbosClientBuilder(target).withTimeout(timeout);
        if (plaintext) {
            builder.withPlaintext();
        }
        return builder.buildBlockingClient();
    }

    /** See {@link CerbosHealthIndicator}'s own javadoc. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnBean(CerbosBlockingClient.class)
    public CerbosHealthIndicator cerbosHealthIndicator(CerbosBlockingClient client) {
        return new CerbosHealthIndicator(client);
    }
}
