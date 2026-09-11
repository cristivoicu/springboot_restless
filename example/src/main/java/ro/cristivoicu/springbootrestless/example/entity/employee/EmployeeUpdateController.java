package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.update.UpdateController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/employees")
public class EmployeeUpdateController extends UpdateController<Employee, Long, EmployeeUpdateModel> {

    private final EmployeeMapper mapper;

    public EmployeeUpdateController(EmployeeUpdateDataSource dataSource, EmployeeMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Mapper<Employee, ?> getEntityMapper() {
        return mapper;
    }
}
