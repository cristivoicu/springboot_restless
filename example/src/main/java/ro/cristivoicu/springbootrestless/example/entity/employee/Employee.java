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
     * <p>
     * No {@code @OneToMany List<Project> projects} here (there was one, briefly) - which projects
     * this employee is on is a many-to-many now (see {@link
     * ro.cristivoicu.springbootrestless.example.entity.assignment.ProjectAssignment}), and a
     * person's own assignment count is unbounded on this codebase's own terms too (nothing stops
     * someone being added to hundreds of projects) - so it gets the same paginated-resource
     * treatment as the project side: {@code GET /project-assignments?employeeId={id}}, not a field
     * on this entity or an embed on {@code EmployeeDto}.
     */
    private String departmentCode;
}
