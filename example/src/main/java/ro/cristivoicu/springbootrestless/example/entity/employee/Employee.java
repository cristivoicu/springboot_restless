package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    /**
     * Deliberately sensitive: demonstrates {@code CerbosFieldMasker} in {@link EmployeeMapper} -
     * hidden from the DTO for managers who lack the {@code canViewSalary} JWT attribute, always
     * visible to admins. See {@code policies/employee.yaml}'s {@code view} action.
     */
    private BigDecimal salary;

    /**
     * Which {@link ro.cristivoicu.springbootrestless.example.entity.department.Department}'s
     * {@code code} this employee belongs to - not a JPA {@code @ManyToOne} (this codebase keeps
     * every entity flat, no mapped associations anywhere), just a matching natural key. Read back
     * by {@code ProjectAuthorizationGuardBean} (via {@code EmployeeRepository.findByEmail}, the
     * authenticated principal's own row) to answer "what's my department" for {@code
     * policies/project.yaml}'s row-scoping rule - see its javadoc.
     */
    private String departmentCode;
}
