package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CerbosContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.testcontainers.containers.BindMode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CerbosHealthIndicator} against a real PDP (a live one reports {@code UP}) and against an
 * unreachable target (a definitely-nothing-listening port, cheaper than starting and stopping a
 * second container just to prove the down path - {@link CerbosAuthorizationGuardFailClosedIT}
 * already covers the "PDP dies mid-request" scenario at the guard level).
 */
class CerbosHealthIndicatorTest {

    private static final CerbosContainer CERBOS = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", BindMode.READ_ONLY);

    @BeforeAll
    static void startContainer() {
        CERBOS.start();
    }

    @AfterAll
    static void stopContainer() {
        CERBOS.stop();
    }

    @Test
    void reportsUpWhenThePdpIsReachable() throws CerbosClientBuilder.InvalidClientConfigurationException {
        CerbosBlockingClient client = new CerbosClientBuilder(CERBOS.getTarget()).withPlaintext().buildBlockingClient();

        assertThat(new CerbosHealthIndicator(client).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWithDetailsWhenThePdpIsUnreachable() throws CerbosClientBuilder.InvalidClientConfigurationException {
        // Port 1 (a reserved, never-listening TCP port) rather than a stopped container - avoids
        // paying for a second container lifecycle just to prove this one path.
        CerbosBlockingClient client = new CerbosClientBuilder("localhost:1")
                .withPlaintext()
                .withTimeout(Duration.ofSeconds(2))
                .buildBlockingClient();

        var health = new CerbosHealthIndicator(client).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("cerbosStatusCode");
    }
}
