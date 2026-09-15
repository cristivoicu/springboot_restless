package ro.cristivoicu.springbootrestless.example.entity.employee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.cerbos.CerbosHiddenField;
import ro.cristivoicu.springbootrestless.models.EntityDto;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDto implements EntityDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;

    /** Masked by {@link EmployeeMapper} via {@code CerbosFieldMasker} - see {@link Employee#getSalary()}. */
    @CerbosHiddenField
    private BigDecimal salary;

    private String departmentCode;
}
