package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.*;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stage 3 negative test: two {@code @RestlessResource} beans claiming the same base path must
 * fail registration fast and attributably, rather than one silently shadowing the other or
 * Spring's own "Ambiguous mapping" error surfacing with no indication which resources collided.
 * <p>
 * Unit-level (no Spring context): {@link RestlessRegistrar}'s own duplicate check runs before
 * either resource touches a real {@code RequestMappingHandlerMapping}, so the collaborators
 * below are minimal hand-built/mocked doubles, not a full application context.
 */
class RestlessRegistrarDuplicateBasePathTest {

    @RestlessResource(basePath = "/collide")
    static class ResourceA extends GizmoLikeResource {
    }

    @RestlessResource(basePath = "/collide")
    static class ResourceB extends GizmoLikeResource {
    }

    /**
     * Shared shape for the two colliding test resources - real (not mocked) DataSource/Mapper
     * instances so {@code GenericTypeResolver} resolves genuine generic type arguments.
     */
    static class GizmoLikeResource extends RestlessResourceHandler<Gizmo, Long> {
        private final DefaultCreateDataSource<Gizmo, Long, GizmoCreateModel> createDataSource =
                new DefaultCreateDataSource<>(null, Gizmo.class, GizmoCreateModel.class);
        private final DefaultReadDataSource<Gizmo, Long, GizmoSearchDto> readDataSource =
                new DefaultReadDataSource<>(null, GizmoSearchDto.class);
        private final DefaultUpdateDataSource<Gizmo, Long, GizmoUpdateModel> updateDataSource =
                new DefaultUpdateDataSource<>(null, GizmoUpdateModel.class);
        private final DefaultDeleteDataSource<Gizmo, Long> deleteDataSource =
                new DefaultDeleteDataSource<>(null, Long.class);
        private final GizmoMapper mapper = new GizmoMapper();

        @Override
        protected CreateDataSource<Gizmo, Long, ?> getCreateDataSource() {
            return createDataSource;
        }

        @Override
        protected ReadDataSource<Gizmo, Long, ?> getReadDataSource() {
            return readDataSource;
        }

        @Override
        protected UpdateDataSource<Gizmo, Long, ?> getUpdateDataSource() {
            return updateDataSource;
        }

        @Override
        protected DeleteDataSource<Gizmo, Long, ?> getDeleteDataSource() {
            return deleteDataSource;
        }

        @Override
        protected Mapper<Gizmo, ?> getEntityMapper() {
            return mapper;
        }

        @Override
        protected Mapper<Gizmo, ?> getOverviewMapper() {
            return mapper;
        }

        @Override
        protected Mapper<Gizmo, ?> getSelectMapper() {
            return mapper;
        }

        @Override
        protected Specification<Gizmo> getSpecification(SearchDto searchDto) {
            return (root, query, cb) -> cb.conjunction();
        }
    }

    @Test
    void duplicateBasePathFailsFastWithAnAttributableMessage() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("resourceA", new ResourceA());
        resources.put("resourceB", new ResourceB());
        when(applicationContext.getBeansWithAnnotation(RestlessResource.class)).thenReturn(resources);

        RequestMappingHandlerMapping requestMappingHandlerMapping = mock(RequestMappingHandlerMapping.class);
        when(requestMappingHandlerMapping.getBuilderConfiguration()).thenReturn(new RequestMappingInfo.BuilderConfiguration());

        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics> metricsProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(metricsProvider.getIfAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics.NONE);

        RestlessRegistrar registrar = new RestlessRegistrar(
                applicationContext, requestMappingHandlerMapping,
                new tools.jackson.databind.ObjectMapper(),
                org.springframework.core.convert.support.DefaultConversionService.getSharedInstance(),
                new Validator() {
                    @Override
                    public boolean supports(Class<?> clazz) {
                        return true;
                    }

                    @Override
                    public void validate(Object target, Errors errors) {
                        // unused: registration never invokes handler methods
                    }
                },
                ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver.NONE,
                null,
                metricsProvider);

        assertThatThrownBy(registrar::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/collide");
    }
}
