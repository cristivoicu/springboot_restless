package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

/**
 * Stage 3 proof: a second entity exposed purely by writing its DataSources/Mapper/resource
 * bean - no hand-written controllers, no changes to RestlessRegistrar or
 * RestlessResourceHandler. Mounted directly at "/departments" since, unlike Employee, nothing
 * else claims that path.
 */
@Component
@RestlessResource(basePath = "/departments")
public class DepartmentRestlessResource extends RestlessResourceHandler<Department, Long> {

    private final DepartmentCreateDataSource createDataSource;
    private final DepartmentReadDataSource readDataSource;
    private final DepartmentUpdateDataSource updateDataSource;
    private final DepartmentDeleteDataSource deleteDataSource;
    private final DepartmentMapper mapper;

    public DepartmentRestlessResource(DepartmentCreateDataSource createDataSource,
                                       DepartmentReadDataSource readDataSource,
                                       DepartmentUpdateDataSource updateDataSource,
                                       DepartmentDeleteDataSource deleteDataSource,
                                       DepartmentMapper mapper) {
        this.createDataSource = createDataSource;
        this.readDataSource = readDataSource;
        this.updateDataSource = updateDataSource;
        this.deleteDataSource = deleteDataSource;
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

    @Override
    protected Specification<Department> getSpecification(SearchDto searchDto) {
        DepartmentSearchDto dto = (DepartmentSearchDto) searchDto;
        return (root, query, cb) -> StringUtils.hasText(dto.getName())
                ? cb.equal(root.get("name"), dto.getName())
                : cb.conjunction();
    }
}
