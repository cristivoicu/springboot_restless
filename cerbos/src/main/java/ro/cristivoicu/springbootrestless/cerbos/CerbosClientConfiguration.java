package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a {@link CerbosBlockingClient} from plain properties, so a consumer only needs to point
 * {@code cerbos.client.target} at a running PDP (a real deployment, or a
 * {@code dev.cerbos.sdk.CerbosContainer} in tests) to get a usable client - not itself a
 * {@code @AutoConfiguration} (this module has no {@code spring.factories}/{@code
 * AutoConfiguration.imports} entry), so it only takes effect where a consumer explicitly imports
 * or component-scans this package. Kept deliberately separate from any authentication concern
 * (JWT decoding, {@code SecurityFilterChain}, ...) - that stays the consumer's own configuration,
 * per this module's design (see the plan this shipped under).
 */
@Configuration
public class CerbosClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CerbosBlockingClient cerbosBlockingClient(
            @Value("${cerbos.client.target:localhost:3593}") String target,
            @Value("${cerbos.client.plaintext:true}") boolean plaintext) throws CerbosClientBuilder.InvalidClientConfigurationException {
        CerbosClientBuilder builder = new CerbosClientBuilder(target);
        if (plaintext) {
            builder.withPlaintext();
        }
        return builder.buildBlockingClient();
    }
}
