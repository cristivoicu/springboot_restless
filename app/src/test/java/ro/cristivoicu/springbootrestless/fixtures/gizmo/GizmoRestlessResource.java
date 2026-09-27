package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

/**
 * Test-only fixture: proves fully-default CUD (all four verbs via {@code Default*DataSource},
 * no hand-written {@code *DataSource} classes at all) still works as a framework mechanism,
 * independent of any specific example entity.
 */
@Component
@RestlessResource(basePath = "/gizmos", allowAll = true)
public class GizmoRestlessResource extends RestlessResourceHandler<Gizmo, Long> {

    private final CreateDataSource<Gizmo, Long, GizmoCreateModel> createDataSource;
    private final ReadDataSource<Gizmo, Long, GizmoSearchDto> readDataSource;
    private final UpdateDataSource<Gizmo, Long, GizmoUpdateModel> updateDataSource;
    private final DeleteDataSource<Gizmo, Long, ?> deleteDataSource;
    private final GizmoMapper mapper;

    public GizmoRestlessResource(GizmoRepository repository, GizmoMapper mapper) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Gizmo.class, GizmoCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, GizmoSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, GizmoUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository, Long.class);
        this.mapper = mapper;
    }

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

    // getSpecification() intentionally not overridden - the default equality-match filter
    // covers the "name" search field.
}
