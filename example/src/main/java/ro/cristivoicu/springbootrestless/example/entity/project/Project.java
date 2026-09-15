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

/**
 * Compile-time generated again, now that a generated resource can carry a real {@code
 * AuthorizationGuard} too: {@code RestlessEntityProcessor} generates {@code ProjectRepository},
 * {@code ProjectMapper} (reflective - {@link ProjectDto}'s fields already match this entity's own
 * by name, so no {@code @RestlessMapperExclude} is needed anywhere), and {@code
 * ProjectRestlessResource} from just this annotation. Only two hand-written pieces remain, both
 * pointed at explicitly since neither follows a "generate a sensible default" convention:
 * {@link ProjectCreateDataSource} (defaulting a blank description) and {@link
 * ProjectAuthorizationGuardBean} (row-scoped by department for non-managers, see {@code
 * policies/project.yaml} - a generic {@code CerbosAuthorizationGuard<E>} needs a small named
 * delegating bean to satisfy {@code authorizationGuard}'s one-concrete-class limit; see that
 * bean's own javadoc).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/projects", createDataSource = ProjectCreateDataSource.class,
        authorizationGuard = ProjectAuthorizationGuardBean.class)
public class Project {

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
