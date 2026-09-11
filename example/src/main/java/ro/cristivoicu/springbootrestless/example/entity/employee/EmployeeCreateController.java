package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.create.CreateController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/employees")
public class EmployeeCreateController extends CreateController<Employee, Long, EmployeeCreateModel> {

    private final EmployeeMapper mapper;

    public EmployeeCreateController(EmployeeCreateDataSource dataSource, EmployeeMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Mapper<Employee, ?> getEntityMapper() {
        return mapper;
    }
}
