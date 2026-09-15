package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires a {@link CerbosBlockingClient} from plain properties, so a consumer only needs to point
 * {@code cerbos.client.target} at a running PDP (a real deployment, or a
 * {@code dev.cerbos.sdk.CerbosContainer} in tests) to get a usable client - not itself a
 * {@code @AutoConfiguration} (this module has no {@code spring.factories}/{@code
 * AutoConfiguration.imports} entry), so it only takes effect where a consumer explicitly imports
 * or component-scans this package. Kept deliberately separate from any authentication concern
 * (JWT decoding, {@code SecurityFilterChain}, ...) - that stays the consumer's own configuration,
 * per this module's design (see the plan this shipped under).
 * <p>
 * {@code cerbos.client.timeout} sets the gRPC deadline {@link CerbosBlockingClient} applies to
 * every call (the SDK defaults to 1s if never configured at all; explicit here so it's a visible,
 * tunable property rather than an implicit SDK default). This is what bounds how long a slow or
 * unreachable PDP can hold up a guarded request before {@link CerbosAuthorizationGuard} gets a
 * chance to fail closed - see its javadoc.
 */
@Configuration
public class CerbosClientConfiguration {

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
}
