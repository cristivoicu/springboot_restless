package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * This app's one demo of optimistic concurrency: {@link #version} is a plain {@code @Version}
 * field - the framework needs no other code for {@code RestlessExceptionHandler} to map a stale
 * concurrent write to 409 automatically. The opt-in half - an {@code If-Match} precondition on
 * single-item {@code PUT}/{@code PATCH}/{@code DELETE}, 412 on a stale value - is what {@link
 * EmployeeDto#getVersion()} exists for: a client reads the current version back, then sends it as
 * {@code If-Match} on its next write. Two managers editing the same employee record at once is
 * the realistic scenario this proves against.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

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

    /**
     * Position on the fixed {@link JobTitle} career ladder - defaults to {@link
     * JobTitle#ASSOCIATE} on create (see {@code EmployeeCreateDataSource}), moved only by the
     * {@code promote} write action ({@link EmployeeRestlessResource#getCustomWriteActions()}),
     * never by a full-replace {@code PUT} - see {@link JobTitle}'s own javadoc for why.
     */
    @Enumerated(EnumType.STRING)
    private JobTitle jobTitle;

    /** Appended only via the {@code addCertification} write action - see {@link Certification}'s own javadoc. */
    @ElementCollection
    @CollectionTable(name = "employee_certification", joinColumns = @JoinColumn(name = "employee_id"))
    private List<Certification> certifications = new ArrayList<>();

    /** Appended only via the {@code recordAchievement} write action - see {@link Achievement}'s own javadoc. */
    @ElementCollection
    @CollectionTable(name = "employee_achievement", joinColumns = @JoinColumn(name = "employee_id"))
    private List<Achievement> achievements = new ArrayList<>();
}
