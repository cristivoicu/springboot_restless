package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/employees")
public class EmployeeDeleteController extends DeleteController<Employee, Long, EmployeeDeleteModel> {

    private final EmployeeMapper mapper;

    public EmployeeDeleteController(EmployeeDeleteDataSource dataSource, EmployeeMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Mapper<Employee, ?> getEntityMapper() {
        return mapper;
    }
}
