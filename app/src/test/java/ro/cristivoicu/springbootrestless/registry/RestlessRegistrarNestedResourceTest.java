package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ro.cristivoicu.springbootrestless.annotation.RestlessParent;
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
 * Feature B items 1/6: templated {@code basePath} registration and duplicate detection for
 * nested resources - unit-level, same minimal hand-built-doubles idiom {@code
 * RestlessRegistrarDuplicateBasePathTest} already uses (no Spring context, no database).
 */
class RestlessRegistrarNestedResourceTest {

    @RestlessResource(basePath = "/events/{eventId}/guests", allowAll = true,
            parent = @RestlessParent(variable = "eventId", field = "name"))
    static class ResourceA extends GizmoLikeResource {
    }

    // Same template as ResourceA ("/events/{anything}/guests") under a DIFFERENT path variable
    // name - must still collide (Feature B item 1: "{x}"/"{y}" are the same route template to
    // Spring MVC regardless of what either resource calls its own variable).
    @RestlessResource(basePath = "/events/{id}/guests", allowAll = true,
            parent = @RestlessParent(variable = "id", field = "name"))
    static class ResourceB extends GizmoLikeResource {
    }

    @RestlessResource(basePath = "/events/{eventId}/photos", allowAll = true,
            parent = @RestlessParent(variable = "missingVariable", field = "name"))
    static class ResourceWithAmbiguousParent extends GizmoLikeResource {
    }

    /** Shared shape for every test resource here - see {@code RestlessRegistrarDuplicateBasePathTest}'s own javadoc for why real (not mocked) DataSource/Mapper instances. */
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
    void templatedBasePathsCollidingOnlyInTheirOwnVariableNameAreStillDuplicates() {
        RestlessRegistrar registrar = registrarFor(new ResourceA(), new ResourceB());

        assertThatThrownBy(registrar::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/events/{id}/guests");
    }

    @Test
    void parentVariableMissingFromBasePathFailsFastAtStartup() {
        RestlessRegistrar registrar = registrarFor(new ResourceWithAmbiguousParent());

        assertThatThrownBy(registrar::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missingVariable");
    }

    private RestlessRegistrar registrarFor(RestlessResourceHandler<?, ?>... resources) {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        Map<String, Object> beans = new LinkedHashMap<>();
        for (int i = 0; i < resources.length; i++) {
            beans.put("resource" + i, resources[i]);
        }
        when(applicationContext.getBeansWithAnnotation(RestlessResource.class)).thenReturn(beans);

        RequestMappingHandlerMapping requestMappingHandlerMapping = mock(RequestMappingHandlerMapping.class);
        when(requestMappingHandlerMapping.getBuilderConfiguration()).thenReturn(new RequestMappingInfo.BuilderConfiguration());

        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics> metricsProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(metricsProvider.getIfAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics.NONE);

        return new RestlessRegistrar(
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
                metricsProvider,
                new ro.cristivoicu.springbootrestless.autoconfigure.RestlessProperties());
    }
}
