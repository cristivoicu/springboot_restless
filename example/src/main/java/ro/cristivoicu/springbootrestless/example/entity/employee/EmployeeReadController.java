package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.read.ReadController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/employees")
public class EmployeeReadController extends ReadController<Employee, Long, EmployeeSearchDto, EmployeeDto> {

    private final EmployeeMapper mapper;

    public EmployeeReadController(EmployeeReadDataSource dataSource, EmployeeMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Specification<Employee> getSpecification(EmployeeSearchDto searchDto) {
        return (root, query, cb) -> StringUtils.hasText(searchDto.getLastName())
                ? cb.equal(root.get("lastName"), searchDto.getLastName())
                : cb.conjunction();
    }

    @Override
    protected Mapper<Employee, EmployeeDto> getSelectMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Employee, EmployeeDto> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Employee, EmployeeDto> getEntityMapper() {
        return mapper;
    }
}
