package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

/** Backs the {@code contact} named view - see {@link EmployeeContactDto}'s own javadoc. */
@Component
public class EmployeeContactMapper implements Mapper<Employee, EmployeeContactDto> {

    @Override
    public EmployeeContactDto map(Employee source) {
        EmployeeContactDto dto = new EmployeeContactDto();
        dto.setId(source.getId());
        dto.setFirstName(source.getFirstName());
        dto.setLastName(source.getLastName());
        dto.setEmail(source.getEmail());
        dto.setInitials(source.getFirstName().substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + source.getLastName().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        return dto;
    }
}
