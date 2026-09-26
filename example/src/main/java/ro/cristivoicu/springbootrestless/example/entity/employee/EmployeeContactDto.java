package ro.cristivoicu.springbootrestless.example.entity.employee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

/**
 * A different bounded-context shape of {@link Employee} than {@link EmployeeDto}: no {@code
 * salary}, no {@code departmentCode}, just what a "contact card" view needs - the worked example
 * of {@code RestlessResourceHandler#getNamedViews()}, served at {@code GET
 * /employees/{id}/contact} alongside the default {@code GET /employees/{id}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeContactDto implements EntityDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String initials;
}
