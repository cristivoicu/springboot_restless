package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

import java.math.BigDecimal;

@Getter
@Setter
public class EmployeeUpdateModel implements UpdateModel {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    /** Optional - unset means "no salary recorded yet". */
    private BigDecimal salary;

    /** Optional - unset means this employee isn't attributed to a department yet. */
    private String departmentCode;
}
