package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

@Component
public class EmployeeCreateDataSource extends CreateDataSource<Employee, Long, EmployeeCreateModel> {

    protected EmployeeCreateDataSource(EmployeeRepository repository) {
        super(repository);
    }

    @Override
    public Employee create(EmployeeCreateModel createDto) {
        Employee employee = new Employee();
        employee.setFirstName(createDto.getFirstName());
        employee.setLastName(createDto.getLastName());
        employee.setEmail(createDto.getEmail());
        employee.setSalary(createDto.getSalary());
        employee.setDepartmentCode(createDto.getDepartmentCode());
        employee.setJobTitle(createDto.getJobTitle() != null ? createDto.getJobTitle() : JobTitle.ASSOCIATE);
        return specificationRepository.save(employee);
    }
}
