package ro.cristivoicu.springbootrestless.entity.employee;

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
import ro.cristivoicu.springbootrestless.resource.ReadAction;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Map;

/**
 * Stage 1/2 proof-of-concept: the same Employee create/read/update/delete data sources and
 * mapper the Stage-0 controllers use, collapsed onto one {@link RestlessResourceHandler}
 * so its HTTP routes can be registered dynamically instead of via four hand-written controllers.
 */
@Component
@RestlessResource(basePath = "/employees-dynamic")
public class EmployeeRestlessResource extends RestlessResourceHandler<Employee, Long> {

    private final EmployeeCreateDataSource createDataSource;
    private final EmployeeReadDataSource readDataSource;
    private final EmployeeUpdateDataSource updateDataSource;
    private final EmployeeDeleteDataSource deleteDataSource;
    private final EmployeeMapper mapper;

    public EmployeeRestlessResource(EmployeeCreateDataSource createDataSource,
                                     EmployeeReadDataSource readDataSource,
                                     EmployeeUpdateDataSource updateDataSource,
                                     EmployeeDeleteDataSource deleteDataSource,
                                     EmployeeMapper mapper) {
        this.createDataSource = createDataSource;
        this.readDataSource = readDataSource;
        this.updateDataSource = updateDataSource;
        this.deleteDataSource = deleteDataSource;
        this.mapper = mapper;
    }

    @Override
    protected CreateDataSource<Employee, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Employee, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Employee, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Employee, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Employee, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Employee, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Employee, ?> getSelectMapper() {
        return mapper;
    }

    @Override
    protected Specification<Employee> getSpecification(SearchDto searchDto) {
        EmployeeSearchDto dto = (EmployeeSearchDto) searchDto;
        return (root, query, cb) -> StringUtils.hasText(dto.getLastName())
                ? cb.equal(root.get("lastName"), dto.getLastName())
                : cb.conjunction();
    }

    /**
     * Demonstrates a custom read action: a suffix {@code LIKE} on email domain, something the
     * default equality-match filter (used above for the main search) can't express.
     */
    @Override
    public Map<String, ReadAction<Employee, ?>> getCustomReadActions() {
        return Map.of("byEmailDomain", new ReadAction<Employee, EmployeeEmailDomainSearchDto>() {
            @Override
            public Class<EmployeeEmailDomainSearchDto> getSearchDtoType() {
                return EmployeeEmailDomainSearchDto.class;
            }

            @Override
            public Specification<Employee> buildSpecification(EmployeeEmailDomainSearchDto searchDto) {
                return (root, query, cb) -> StringUtils.hasText(searchDto.getDomain())
                        ? cb.like(root.get("email"), "%@" + searchDto.getDomain())
                        : cb.conjunction();
            }
        });
    }
}
