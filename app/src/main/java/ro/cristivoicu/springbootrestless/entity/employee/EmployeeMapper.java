package ro.cristivoicu.springbootrestless.entity.employee;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class EmployeeMapper implements Mapper<Employee, EmployeeDto> {
    @Override
    public EmployeeDto map(Employee source) {
        return new EmployeeDto(source.getId(), source.getFirstName(), source.getLastName(), source.getEmail());
    }
}
