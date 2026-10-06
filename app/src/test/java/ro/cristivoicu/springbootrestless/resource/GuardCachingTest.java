package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.HandlerMapping;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ground rules Phase 2 item 14 ("Internals"): {@code getAuthorizationGuard()} must be resolved
 * once, in {@code init()}, not re-invoked on every {@code checkPreCheck}/{@code checkCanAccess}
 * call - the README's own documented pattern constructs a new guard instance per call, which
 * used to mean a fresh instance (and whatever construction cost it carries) on every single
 * request. Pure unit test, no Spring context - calls the {@code public final} handler methods
 * directly, same direct-construction pattern {@code FilterBindingFailFastTest} already uses.
 */
class GuardCachingTest {

    static class Thing {
        @Id
        private Long id;
        private String name;
    }

    static class ThingSearchDto extends AbstractSearchDto {
    }

    private static final AtomicInteger GUARD_RESOLUTIONS = new AtomicInteger();

    private abstract static class ThingResource extends RestlessResourceHandler<Thing, Long> {
        @Override
        public Set<AuthorizationGuard.Action> getEnabledOperations() {
            return Set.of(AuthorizationGuard.Action.READ_ONE);
        }

        @Override
        protected Mapper<Thing, ?> getEntityMapper() {
            return source -> source;
        }

        @Override
        protected Mapper<Thing, ?> getOverviewMapper() {
            return getEntityMapper();
        }

        @Override
        protected Mapper<Thing, ?> getSelectMapper() {
            return getEntityMapper();
        }

        @Override
        protected AuthorizationGuard<Thing> getAuthorizationGuard() {
            GUARD_RESOLUTIONS.incrementAndGet();
            return AuthorizationGuard.allowAll();
        }
    }

    @Test
    void getAuthorizationGuardIsResolvedExactlyOnceRegardlessOfRequestCount() throws Exception {
        GUARD_RESOLUTIONS.set(0);
        DefaultReadDataSource<Thing, Long, ThingSearchDto> readDataSource =
                new DefaultReadDataSource<>(null, ThingSearchDto.class) {
                    @Override
                    public Thing findOne(Long id) {
                        return new Thing();
                    }
                };
        ThingResource resource = new ThingResource() {
            @Override
            protected ReadDataSource<Thing, Long, ?> getReadDataSource() {
                return readDataSource;
            }
        };

        resource.init(new RestlessInitContext(resource.resolveMetadata("/things"),
                new tools.jackson.databind.ObjectMapper(), DefaultConversionService.getSharedInstance(),
                noOpValidator(), RestlessEmbedResolver.NONE, null, RestlessAuthorizationMetrics.NONE,
                10_000, 2_000, 1_000));

        // init() itself already resolves the guard once - confirm, then prove further requests
        // don't resolve it again.
        assertThat(GUARD_RESOLUTIONS.get()).isEqualTo(1);

        for (int i = 0; i < 3; i++) {
            resource.findOne(requestForId(1L));
        }

        assertThat(GUARD_RESOLUTIONS.get()).isEqualTo(1);
    }

    private static MockHttpServletRequest requestForId(Long id) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/things/" + id);
        Map<String, String> vars = new HashMap<>();
        vars.put("id", String.valueOf(id));
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, vars);
        return request;
    }

    private static Validator noOpValidator() {
        return new Validator() {
            @Override
            public boolean supports(Class<?> clazz) {
                return true;
            }

            @Override
            public void validate(Object target, Errors errors) {
            }
        };
    }
}
