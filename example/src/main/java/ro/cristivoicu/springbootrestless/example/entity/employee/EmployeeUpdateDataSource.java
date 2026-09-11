package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;

@Component
public class EmployeeUpdateDataSource extends UpdateDataSource<Employee, Long, EmployeeUpdateModel> {

    protected EmployeeUpdateDataSource(EmployeeRepository repository) {
        super(repository);
    }

    @Override
    public Employee update(Long id, EmployeeUpdateModel updateDto) {
        Employee employee = specificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee " + id + " not found"));
        employee.setFirstName(updateDto.getFirstName());
        employee.setLastName(updateDto.getLastName());
        employee.setEmail(updateDto.getEmail());
        return specificationRepository.save(employee);
    }
}
