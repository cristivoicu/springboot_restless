package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

/**
 * Stage 3 proof (runtime registration): a second entity exposed purely by writing its
 * DataSources/Mapper/resource bean - no hand-written controllers.
 * <p>
 * Stage 1 proof (default CUD): Create/Update/Delete are the framework's default,
 * reflection-based implementations - Department declares no {@code *DataSource} classes of its
 * own at all for those three verbs, only the read side (search logic stays hand-written) and
 * the response {@link DepartmentMapper}.
 */
@Component
@RestlessResource(basePath = "/departments")
public class DepartmentRestlessResource extends RestlessResourceHandler<Department, Long> {

    private final CreateDataSource<Department, Long, DepartmentCreateModel> createDataSource;
    private final DepartmentReadDataSource readDataSource;
    private final UpdateDataSource<Department, Long, DepartmentUpdateModel> updateDataSource;
    private final DeleteDataSource<Department, Long, ?> deleteDataSource;
    private final DepartmentMapper mapper;

    public DepartmentRestlessResource(DepartmentRepository repository,
                                       DepartmentReadDataSource readDataSource,
                                       DepartmentMapper mapper) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Department.class, DepartmentCreateModel.class);
        this.readDataSource = readDataSource;
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, DepartmentUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository, Long.class);
        this.mapper = mapper;
    }

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

    // getSpecification() intentionally not overridden: RestlessResourceHandler's default
    // (equality-match on populated DepartmentSearchDto fields) is behavior-identical to the
    // hand-written StringUtils.hasText(name) check this used to have.
}
