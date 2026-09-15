package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CerbosContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.BindMode;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard.Action;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.Widget;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof of {@link CerbosAuthorizationGuard}'s fail-closed behavior (see its javadoc) - a
 * dedicated container and client, entirely separate from {@link CerbosAuthorizationGuardIT}'s
 * shared singleton one, precisely so stopping it mid-test can't destabilize every other test in
 * this module that happens to run afterward. One test method only, deliberately: JUnit gives no
 * ordering guarantee between methods in a class, so "the container is stopped" has to be a state
 * this class only ever reaches once, at the very end, never observable by a sibling test.
 */
class CerbosAuthorizationGuardFailClosedIT {

    private static final CerbosContainer CERBOS = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", BindMode.READ_ONLY);

    private static CerbosBlockingClient client;

    @BeforeAll
    static void startContainerAndClient() throws CerbosClientBuilder.InvalidClientConfigurationException {
        CERBOS.start();
        // A short deadline: once the container is stopped, the port simply refuses connections
        // (fast to fail on its own), but a tight timeout keeps this test's worst case bounded
        // regardless of exactly how the underlying gRPC channel reacts to that.
        client = new CerbosClientBuilder(CERBOS.getTarget())
                .withPlaintext()
                .withTimeout(Duration.ofSeconds(2))
                .buildBlockingClient();
    }

    @AfterAll
    static void stopContainer() {
        // Already stopped by the test itself in the normal case; harmless (Testcontainers
        // tolerates a redundant stop()) if some earlier assertion failed before it got there.
        CERBOS.stop();
    }

    @Test
    void guardFailsClosedOncePdpBecomesUnreachableMidTest() {
        CerbosAuthorizationGuard<Widget> guard = new CerbosAuthorizationGuard<>(client, "widget", Widget::getId,
                CerbosResourceAttributesMapper.reflective(Widget.class));
        authenticateAsAdmin();

        // Baseline: the PDP is up, and this admin principal is unconditionally allowed by
        // policies/widget.yaml - proves the guard (and this test's own setup) works normally
        // before the failure is introduced, so the fail-closed assertions below are meaningful
        // rather than accidentally trivially true.
        assertThat(guard.preCheck(Action.CREATE, null, request())).isTrue();
        assertThat(guard.scope(Action.READ_LIST, null, request())).isNull(); // ALWAYS_ALLOWED

        CERBOS.stop();

        // Same unconditionally-allowed admin principal, same action - every hook now denies,
        // not because the policy changed, but because the guard could not reach the PDP at all.
        assertThat(guard.preCheck(Action.CREATE, null, request())).isFalse();
        assertThat(guard.canAccess(Action.READ_ONE, request(), new Widget(1L, "alpha", 1L, "engineering"))).isFalse();

        Specification<Widget> scope = guard.scope(Action.READ_LIST, null, request());
        assertThat(scope).isNotNull(); // not ALWAYS_ALLOWED's null - a real deny-all predicate
    }

    private void authenticateAsAdmin() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("admin-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("admin"))));
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }
}
