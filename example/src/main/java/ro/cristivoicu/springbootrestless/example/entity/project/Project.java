package ro.cristivoicu.springbootrestless.example.entity.project;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.models.AbstractAuditableEntity;

/**
 * Back to compile-time generated with just one hand-written piece plus the guard - a project no
 * longer owns "its" employee at all: {@link ro.cristivoicu.springbootrestless.example.entity.assignment.ProjectAssignment}
 * is the many-to-many between {@code Project} and {@link
 * ro.cristivoicu.springbootrestless.example.entity.employee.Employee} now (a team can be large -
 * see that class's own javadoc for why this isn't a JPA {@code @ManyToMany} or a {@code
 * @RestlessEmbed} list here). {@code RestlessEntityProcessor} generates {@code ProjectRepository},
 * {@code ProjectMapper} (reflective again - {@link ProjectDto}'s fields match this entity's own by
 * name, inherited {@code createdDate}/{@code lastModifiedDate} included), and {@code
 * ProjectRestlessResource} from just this annotation.
 * <p>
 * Also this codebase's one demo of {@link AbstractAuditableEntity} - see its own javadoc for why
 * this stays opt-in per entity rather than something every entity gets; {@code
 * ExampleApplication}'s {@code @EnableJpaAuditing} is the other half of the setup.
 * <p>
 * And this app's one demo of API versioning (see the root README's own tutorial section) -
 * {@code version = "1"} forwards onto every route {@code RestlessRegistrar} registers for this
 * resource, resolved by the header-based {@code ApiVersionConfigurer} bean in {@code
 * ExampleApplication} ({@code X-API-Version}, not required - every other route in this app has no
 * version constraint at all and must keep working with no header sent).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/projects", version = "1", createDataSource = ProjectCreateDataSource.class,
        authorizationGuard = ProjectAuthorizationGuardBean.class)
public class Project extends AbstractAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    /**
     * Which {@link ro.cristivoicu.springbootrestless.example.entity.department.Department}'s
     * {@code code} this project belongs to - a matching natural key, not a JPA {@code
     * @ManyToOne} (this codebase keeps every entity flat). {@code policies/project.yaml} scopes
     * non-manager reads to projects whose {@code departmentCode} matches the authenticated
     * principal's own (resolved from their {@link
     * ro.cristivoicu.springbootrestless.example.entity.employee.Employee} row).
     */
    private String departmentCode;
}
