package ro.cristivoicu.springbootrestless.example.entity.project;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Manual — runtime defaults, no codegen (see {@code ProjectRestlessResource}), not the
 * compile-time-generated tier this used to demonstrate: it needs a real {@code
 * CerbosAuthorizationGuard} (row-scoped by department for non-managers, see {@code
 * policies/project.yaml}), and {@code @RestlessEntity}-generated resources have no way to inject
 * one - the annotation has no {@code authorizationGuard} attribute, deliberately (see the design
 * discussion this shipped under: extending the processor for one demo entity was judged not
 * worth the codegen surface). {@link ProjectCreateDataSource} (defaulting a blank description)
 * still applies unchanged; only the wiring moved from generated to hand-written.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
