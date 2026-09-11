package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.junit.jupiter.api.Test;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.*;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.ResourceMetadata;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 1 proof: {@code RestlessResourceHandler.resolveMetadata()} recovers DTO types for
 * directly-instantiated {@code Default*DataSource}s via {@link TypedDataSource}, not via
 * {@code GenericTypeResolver} (which can't see generic arguments erased at the instance level).
 * No Spring context needed - this is pure reflection logic, exercised the same way
 * {@code RestlessRegistrarDuplicateBasePathTest} exercises registration logic standalone.
 */
class DefaultDataSourceTypeResolutionTest {

    static class GizmoDefaultResource extends RestlessResourceHandler<Gizmo, Long> {
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
    }

    @Test
    void resolvesDefaultDataSourceDtoTypesViaTypedDataSource() {
        ResourceMetadata metadata = new GizmoDefaultResource().resolveMetadata("/gizmos");

        assertThat(metadata.entityType()).isEqualTo(Gizmo.class);
        assertThat(metadata.idType()).isEqualTo(Long.class);
        assertThat(metadata.createModelType()).isEqualTo(GizmoCreateModel.class);
        assertThat(metadata.updateModelType()).isEqualTo(GizmoUpdateModel.class);
        assertThat(metadata.deleteModelType()).isEqualTo(ro.cristivoicu.springbootrestless.models.DefaultDeleteModel.class);
        // search DTO resolution now also goes through TypedDataSource, same as the other three.
        assertThat(metadata.searchDtoType()).isEqualTo(GizmoSearchDto.class);
    }
}
