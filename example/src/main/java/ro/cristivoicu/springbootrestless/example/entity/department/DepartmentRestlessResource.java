package ro.cristivoicu.springbootrestless.example.entity.department;

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
 * Stage 3 proof (runtime registration): a second entity exposed purely by writing its
 * Mapper/resource bean - no hand-written controllers, no hand-written {@code *DataSource}
 * classes at all.
 * <p>
 * Stage 1 proof (default CUD) + later step (default read): every verb is the framework's
 * default, reflection-based implementation. Department declares no {@code *DataSource} classes
 * of its own - only the response {@link DepartmentMapper} stays hand-written, deliberately (see
 * {@link Mapper}'s javadoc).
 */
@Component
@RestlessResource(basePath = "/departments")
public class DepartmentRestlessResource extends RestlessResourceHandler<Department, Long> {

    private final CreateDataSource<Department, Long, DepartmentCreateModel> createDataSource;
    private final ReadDataSource<Department, Long, DepartmentSearchDto> readDataSource;
    private final UpdateDataSource<Department, Long, DepartmentUpdateModel> updateDataSource;
    private final DeleteDataSource<Department, Long, ?> deleteDataSource;
    private final DepartmentMapper mapper;

    public DepartmentRestlessResource(DepartmentRepository repository, DepartmentMapper mapper) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Department.class, DepartmentCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, DepartmentSearchDto.class);
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
