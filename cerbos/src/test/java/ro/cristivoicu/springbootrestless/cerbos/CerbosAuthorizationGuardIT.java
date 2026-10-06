package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CerbosContainer;
import dev.cerbos.sdk.builders.AttributeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard.Action;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.Widget;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.WidgetRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof: a real Cerbos PDP (Testcontainers) evaluating {@code
 * src/test/resources/policies/widget.yaml}, through {@link CerbosAuthorizationGuard} and {@link
 * CerbosQueryPlanTranslator}, into an actual filtered JPA query. {@link
 * CerbosQueryPlanTranslatorTest} already covers the translator's operator-by-operator behavior in
 * isolation; this test is the "does the whole pipe actually connect" check.
 */
@DataJpaTest
@Testcontainers
class CerbosAuthorizationGuardIT {

    @Container
    private static final CerbosContainer CERBOS = new CerbosContainer()
            .withClasspathResourceMapping("policies", "/policies", BindMode.READ_ONLY);

    private static CerbosBlockingClient client;

    @Autowired
    private WidgetRepository repository;

    @BeforeAll
    static void startClient() throws CerbosClientBuilder.InvalidClientConfigurationException {
        client = new CerbosClientBuilder(CERBOS.getTarget()).withPlaintext().buildBlockingClient();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanReadEveryWidgetUnscoped() {
        seed();
        CerbosAuthorizationGuard<Widget> guard = widgetGuard();
        authenticateAs("admin-user", "admin", Map.of());

        assertThat(guard.preCheck(Action.CREATE, null, request())).isTrue();

        Specification<Widget> scope = guard.scope(Action.READ_LIST, null, request());
        assertThat(scope).isNull(); // ALWAYS_ALLOWED -> no extra restriction
        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    void ownerIsScopedToTheirOwnWidgetsByRealCerbosQueryPlan() {
        List<Widget> widgets = seed();
        Widget own = widgets.get(0);
        CerbosAuthorizationGuard<Widget> guard = widgetGuard();
        authenticateAs("owner-user", "owner", Map.of("userId", own.getOwnerId()));

        Specification<Widget> scope = guard.scope(Action.READ_LIST, null, request());
        assertThat(scope).isNotNull(); // CONDITIONAL -> translated into a real WHERE clause

        assertThat(repository.findAll(scope)).extracting(Widget::getId).containsExactly(own.getId());
    }

    @Test
    void ownerCanAccessTheirOwnWidgetButNotSomeoneElses() {
        List<Widget> widgets = seed();
        Widget own = widgets.get(0);
        Widget someoneElses = widgets.get(1);
        CerbosAuthorizationGuard<Widget> guard = widgetGuard();
        authenticateAs("owner-user", "owner", Map.of("userId", own.getOwnerId()));

        assertThat(guard.canAccess(Action.READ_ONE, request(), own)).isTrue();
        assertThat(guard.canAccess(Action.READ_ONE, request(), someoneElses)).isFalse();
    }

    @Test
    void principalWithNoMatchingRoleCannotEvenAttemptCreate() {
        CerbosAuthorizationGuard<Widget> guard = widgetGuard();
        authenticateAs("nobody", "unassigned-role", Map.of());

        assertThat(guard.preCheck(Action.CREATE, null, request())).isFalse();
    }

    /**
     * {@code principalAttributesExtender} exists for attributes that don't come from a JWT claim
     * at all - here, "department" is deliberately left off the token itself (unlike {@code
     * ownerId} in the other tests) and supplied only by the extender, proving it's actually
     * merged onto the principal {@link CerbosPrincipalResolver} builds, not read from the token.
     */
    @Test
    void principalAttributesExtenderSuppliesAttributesTheJwtNeverCarried() {
        List<Widget> widgets = seed();
        Widget engineering = widgets.get(0); // department "engineering", see seed()
        Widget sales = widgets.get(1); // department "sales"
        CerbosAuthorizationGuard<Widget> guard = new CerbosAuthorizationGuard<>(client, "widget", Widget::getId,
                CerbosResourceAttributesMapper.reflective(Widget.class), CerbosActionNaming.DEFAULT,
                request -> Map.of("department", AttributeValue.stringValue("engineering")));
        authenticateAs("colleague-user", "colleague", Map.of()); // no department claim on the token

        assertThat(guard.canAccess(Action.READ_ONE, request(), engineering)).isTrue();
        assertThat(guard.canAccess(Action.READ_ONE, request(), sales)).isFalse();
    }

    /**
     * Ground rules Phase 2 item 11 ("Cerbos plan translator"): {@code principalAttributesExtender}
     * is documented to potentially be a real lookup (a database round trip), not just a cheap map
     * read - so resolving it once per {@link HttpServletRequest}, not once per hook call, matters.
     * All three hooks are called against the exact same request instance here (unlike every other
     * test in this class, which calls {@code request()} fresh per hook) specifically so the
     * extender's call count is a meaningful assertion.
     */
    @Test
    void principalAttributesExtenderIsConsultedOnceNoMatterHowManyHooksRunAgainstTheSameRequest() {
        List<Widget> widgets = seed();
        Widget own = widgets.get(0);
        AtomicInteger extenderCalls = new AtomicInteger();
        CerbosAuthorizationGuard<Widget> guard = new CerbosAuthorizationGuard<>(client, "widget", Widget::getId,
                CerbosResourceAttributesMapper.reflective(Widget.class), CerbosActionNaming.DEFAULT,
                request -> {
                    extenderCalls.incrementAndGet();
                    return Map.of();
                });
        authenticateAs("owner-user", "owner", Map.of("userId", own.getOwnerId()));
        MockHttpServletRequest sameRequest = request();

        guard.preCheck(Action.CREATE, null, sameRequest);
        guard.canAccess(Action.READ_ONE, sameRequest, own);
        guard.scope(Action.READ_LIST, null, sameRequest);

        assertThat(extenderCalls.get()).isEqualTo(1);
    }

    private CerbosAuthorizationGuard<Widget> widgetGuard() {
        // reflective(): the policy needs ownerId/department, but every Widget field (including
        // id/name, which it doesn't) is exposed with no hand-written mapping - proof this path
        // works end-to-end against a real PDP, not just the translator in isolation.
        return new CerbosAuthorizationGuard<>(client, "widget", Widget::getId,
                CerbosResourceAttributesMapper.reflective(Widget.class));
    }

    private List<Widget> seed() {
        return repository.saveAll(List.of(
                new Widget(null, "alpha", 1L, "engineering"),
                new Widget(null, "beta", 2L, "sales"),
                new Widget(null, "gamma", 3L, "marketing")));
    }

    /**
     * Builds a real {@link Jwt} and wraps it in a {@link JwtAuthenticationToken} - exactly what
     * {@code spring-security-oauth2-resource-server} would produce from a validated bearer token
     * - so {@link CerbosPrincipalResolver} exercises its real JWT-claims-to-attributes path
     * rather than a test-only shortcut.
     */
    private void authenticateAs(String subject, String role, Map<String, Object> claims) {
        Jwt.Builder jwtBuilder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(jwtBuilder::claim);
        Jwt jwt = jwtBuilder.build();

        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }
}
