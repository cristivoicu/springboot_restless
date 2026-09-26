package ro.cristivoicu.springbootrestless.example.entity.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.cerbos.CerbosHiddenField;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbed;
import ro.cristivoicu.springbootrestless.example.entity.department.Department;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentDto;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentRestlessResource;
import ro.cristivoicu.springbootrestless.models.EntityDto;

import java.math.BigDecimal;

/**
 * No {@code projects} field here (there was one, briefly) - which projects this employee is
 * assigned to is a many-to-many, unbounded in principle (see {@code
 * ro.cristivoicu.springbootrestless.example.entity.assignment.ProjectAssignment}'s own javadoc),
 * so it gets the same paginated-resource treatment {@code ProjectDto} gets rather than an {@code
 * @RestlessEmbed} list: {@code GET /project-assignments?employeeId={id}}. {@code department}
 * below stays an embed - exactly one row, genuinely bounded, the shape {@code RestlessEmbed} is
 * actually meant for.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDto implements EntityDto {

    @Schema(description = "Unique identifier, assigned by the server on create.", example = "42")
    private Long id;

    @Schema(description = "Optimistic-concurrency version. Send back as the If-Match header on PUT/PATCH/DELETE to guard against overwriting a concurrent change.", example = "0")
    private Long version;

    @Schema(description = "Legal first name.", example = "Ada")
    private String firstName;

    @Schema(description = "Legal last name.", example = "Lovelace")
    private String lastName;

    @Schema(description = "Work email address, unique across employees.", example = "ada.lovelace@example.com")
    private String email;

    /**
     * Computed by {@link EmployeeMapper}, not copied from {@link Employee} (which has no such
     * field) - the worked example of a DTO-only attribute a Cerbos policy condition can reference
     * via {@link ro.cristivoicu.springbootrestless.cerbos.CerbosDtoResourceAttributesMapper},
     * which {@code EmployeeRestlessResource}'s guard is wired with for exactly this. See {@code
     * policies/employee.yaml}'s {@code contact} action rule.
     */
    @Schema(description = "First letters of firstName and lastName, uppercased - a computed, DTO-only field.", example = "AL")
    private String initials;

    /** Masked by {@link EmployeeMapper} via {@code CerbosFieldMasker} - see {@link Employee#getSalary()}. */
    @CerbosHiddenField
    @Schema(description = "Annual salary. Redacted (omitted) for callers without the salary:view permission.", example = "95000.00")
    private BigDecimal salary;

    @Schema(description = "Code of the department this employee belongs to. See department.code.", example = "ENG")
    private String departmentCode;

    /**
     * {@code GET /employees/{id}?expand=department} - joined on {@link
     * Employee#getDepartmentCode()} {@code ==} {@link Department#getCode()}, run through {@code
     * DepartmentRestlessResource}'s own {@code AuthorizationGuard} ({@code policies/department.yaml}:
     * role-only, every authenticated employee/manager/admin already passes it).
     */
    @Schema(description = "Department details, present only when requested via ?expand=department.")
    @RestlessEmbed(resource = DepartmentRestlessResource.class, sourceField = "departmentCode", targetField = "code")
    private DepartmentDto department;
}
