package ro.cristivoicu.springbootrestless.embed;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.Gizmo;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.GizmoMapper;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.GizmoRepository;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.GizmoRestlessResource;
import ro.cristivoicu.springbootrestless.fixtures.widget.Widget;
import ro.cristivoicu.springbootrestless.fixtures.widget.WidgetMapper;
import ro.cristivoicu.springbootrestless.fixtures.widget.WidgetRepository;
import ro.cristivoicu.springbootrestless.fixtures.widget.WidgetRestlessResource;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ground rules item 8 ("Embeds"): {@code findEmbeddedList}/{@code findEmbeddedOne} directly,
 * bypassing {@code RestlessEmbedResolver}/HTTP entirely - every behavior under test here is a
 * property of {@link ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler} itself,
 * and constructing a second handler instance by hand (same pattern {@code
 * RestlessRegistrarDuplicateBasePathTest}/{@code DefaultDataSourceTypeResolutionTest} already use)
 * with a deliberately different {@code getEnabledOperations()}/{@code getAuthorizationGuard()}
 * is far more direct than building a second real {@code @RestlessEmbed}-annotated DTO/entity pair
 * per scenario.
 */
@SpringBootTest
@Transactional
class EmbedHardeningTest {

    @Autowired
    private GizmoRepository gizmoRepository;

    @Autowired
    private GizmoMapper gizmoMapper;

    @Autowired
    private WidgetRepository widgetRepository;

    @Autowired
    private WidgetMapper widgetMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversionService conversionService;

    @Autowired
    private Validator validator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final HttpServletRequest request = new MockHttpServletRequest();

    /** Everything disabled except what each test below re-enables via {@link #init}. */
    static class RestrictedGizmoResource extends GizmoRestlessResource {
        private Set<AuthorizationGuard.Action> enabled = ALL_OPERATIONS;
        private AuthorizationGuard<Gizmo> guard = AuthorizationGuard.allowAll();

        RestrictedGizmoResource(GizmoRepository repository, GizmoMapper mapper) {
            super(repository, mapper);
        }

        @Override
        public Set<AuthorizationGuard.Action> getEnabledOperations() {
            return enabled;
        }

        @Override
        protected AuthorizationGuard<Gizmo> getAuthorizationGuard() {
            return guard;
        }
    }

    private RestrictedGizmoResource initGizmoResource(Set<AuthorizationGuard.Action> enabledOperations,
                                                        AuthorizationGuard<Gizmo> guard) {
        RestrictedGizmoResource resource = new RestrictedGizmoResource(gizmoRepository, gizmoMapper);
        resource.enabled = enabledOperations;
        resource.guard = guard;
        resource.init(new ro.cristivoicu.springbootrestless.resource.RestlessInitContext(
                resource.resolveMetadata("/gizmos-embed-test"), objectMapper, conversionService, validator,
                RestlessEmbedResolver.NONE, transactionManager, ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics.NONE,
                10_000, 2_000, 1_000));
        return resource;
    }

    private WidgetRestlessResource initWidgetResource() {
        WidgetRestlessResource resource = new WidgetRestlessResource(widgetRepository, widgetMapper);
        resource.init(new ro.cristivoicu.springbootrestless.resource.RestlessInitContext(
                resource.resolveMetadata("/widgets-embed-test"), objectMapper, conversionService, validator,
                RestlessEmbedResolver.NONE, transactionManager, ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics.NONE,
                10_000, 2_000, 1_000));
        return resource;
    }

    @Test
    void findEmbeddedListIsEmptyWhenTheTargetDisabledReadList() {
        gizmoRepository.save(new Gizmo(null, "Acme", "CODE", 1));
        RestrictedGizmoResource resource = initGizmoResource(
                Set.of(AuthorizationGuard.Action.READ_ONE), AuthorizationGuard.allowAll());

        java.util.List<?> result = resource.findEmbeddedList((root, query, cb) -> cb.conjunction(), request);

        assertThat(result).isEmpty();
    }

    @Test
    void findEmbeddedOneIsNullWhenTheTargetDisabledReadOne() {
        gizmoRepository.save(new Gizmo(null, "Acme", "CODE", 1));
        RestrictedGizmoResource resource = initGizmoResource(
                Set.of(AuthorizationGuard.Action.READ_LIST), AuthorizationGuard.allowAll());

        Object result = resource.findEmbeddedOne((root, query, cb) -> cb.equal(root.get("name"), "Acme"), request);

        assertThat(result).isNull();
    }

    @Test
    void findEmbeddedOneAppliesScope() {
        gizmoRepository.save(new Gizmo(null, "Acme", "CODE", 1));
        AuthorizationGuard<Gizmo> denyAllScope = new AuthorizationGuard<>() {
            @Override
            public Specification<Gizmo> scope(Action action, String customActionName, HttpServletRequest request) {
                return (root, query, cb) -> cb.disjunction();
            }
        };
        RestrictedGizmoResource resource = initGizmoResource(
                ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler.ALL_OPERATIONS, denyAllScope);

        Object result = resource.findEmbeddedOne((root, query, cb) -> cb.equal(root.get("name"), "Acme"), request);

        assertThat(result).isNull();
    }

    @Test
    void findEmbeddedOneThrowsWhenTheJoinMatchesMoreThanOneRow() {
        gizmoRepository.save(new Gizmo(null, "Acme", "CODE1", 1));
        gizmoRepository.save(new Gizmo(null, "Acme", "CODE2", 2));
        RestrictedGizmoResource resource = initGizmoResource(
                ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler.ALL_OPERATIONS, AuthorizationGuard.allowAll());

        assertThatThrownBy(() -> resource.findEmbeddedOne((root, query, cb) -> cb.equal(root.get("name"), "Acme"), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one");
    }

    @Test
    void findEmbeddedListExcludesSoftDeletedRows() {
        Widget kept = widgetRepository.save(new Widget(null, "Keep", null, false));
        Widget deleted = widgetRepository.save(new Widget(null, "Delete", null, true));
        WidgetRestlessResource resource = initWidgetResource();

        java.util.List<?> result = resource.findEmbeddedList(
                (root, query, cb) -> cb.in(root.get("id")).value(kept.getId()).value(deleted.getId()), request);

        assertThat(result).hasSize(1);
    }

    @Test
    void findEmbeddedOneExcludesASoftDeletedRow() {
        widgetRepository.save(new Widget(null, "Delete", null, true));
        WidgetRestlessResource resource = initWidgetResource();

        Object result = resource.findEmbeddedOne((root, query, cb) -> cb.equal(root.get("name"), "Delete"), request);

        assertThat(result).isNull();
    }
}
