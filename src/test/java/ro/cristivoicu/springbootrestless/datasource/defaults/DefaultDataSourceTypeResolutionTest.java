package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.junit.jupiter.api.Test;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.entity.department.*;
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

    static class DepartmentDefaultResource extends RestlessResourceHandler<Department, Long> {
        private final DefaultCreateDataSource<Department, Long, DepartmentCreateModel> createDataSource =
                new DefaultCreateDataSource<>(null, Department.class, DepartmentCreateModel.class);
        private final DepartmentReadDataSource readDataSource = new DepartmentReadDataSource(null);
        private final DefaultUpdateDataSource<Department, Long, DepartmentUpdateModel> updateDataSource =
                new DefaultUpdateDataSource<>(null, DepartmentUpdateModel.class);
        private final DefaultDeleteDataSource<Department, Long> deleteDataSource =
                new DefaultDeleteDataSource<>(null, Long.class);
        private final DepartmentMapper mapper = new DepartmentMapper();

        @Override
        protected CreateDataSource<Department, Long, ?> getCreateDataSource() {
            return createDataSource;
        }

        @Override
        protected ReadDataSource<Department, Long, ?> getReadDataSource() {
            return readDataSource;
        }

        @Override
        protected UpdateDataSource<Department, Long, ?> getUpdateDataSource() {
            return updateDataSource;
        }

        @Override
        protected DeleteDataSource<Department, Long, ?> getDeleteDataSource() {
            return deleteDataSource;
        }

        @Override
        protected Mapper<Department, ?> getEntityMapper() {
            return mapper;
        }

        @Override
        protected Mapper<Department, ?> getOverviewMapper() {
            return mapper;
        }

        @Override
        protected Mapper<Department, ?> getSelectMapper() {
            return mapper;
        }
    }

    @Test
    void resolvesDefaultDataSourceDtoTypesViaTypedDataSource() {
        ResourceMetadata metadata = new DepartmentDefaultResource().resolveMetadata("/departments");

        assertThat(metadata.entityType()).isEqualTo(Department.class);
        assertThat(metadata.idType()).isEqualTo(Long.class);
        assertThat(metadata.createModelType()).isEqualTo(DepartmentCreateModel.class);
        assertThat(metadata.updateModelType()).isEqualTo(DepartmentUpdateModel.class);
        assertThat(metadata.deleteModelType()).isEqualTo(ro.cristivoicu.springbootrestless.models.DefaultDeleteModel.class);
        // search DTO resolution is untouched by TypedDataSource - still via GenericTypeResolver
        // against the hand-written DepartmentReadDataSource's own generics.
        assertThat(metadata.searchDtoType()).isEqualTo(DepartmentSearchDto.class);
    }
}
