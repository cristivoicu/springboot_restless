package ro.cristivoicu.springbootrestless.example.entity.task;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessParent;
import ro.cristivoicu.springbootrestless.example.entity.project.Project;

/**
 * This codebase's one demo of a nested ({@code @RestlessParent}) resource - mounted at
 * {@code /projects/{projectId}/tasks} rather than a flat {@code /tasks?projectId=...}. {@code
 * field = "project.id"} (a dotted {@code @ManyToOne} association, not a flat scalar column) is
 * the deliberately-harder-to-support form - see backend-nunti's own real integration for the
 * scalar-column case ({@code Guest.eventId}, a plain {@code UUID}), covered separately by an
 * {@code app}-module unit test.
 * <p>
 * {@code RestlessEntityProcessor} (compile-time) already validated that {@code project.id} exists
 * and resolves to a path-variable-convertible type; {@code RestlessResourceHandler} is what
 * actually enforces every runtime semantic this annotation promises - a task's own {@code
 * project} is force-set from the path on create (a client-supplied value, if the create model
 * even offered one, would be overwritten, not honored), protected like {@code @Id} on update,
 * and a {@code GET}/{@code PUT}/{@code PATCH}/{@code DELETE} for a task belonging to a
 * <em>different</em> project 404s exactly like a missing row.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/projects/{projectId}/tasks",
        parent = @RestlessParent(variable = "projectId", field = "project.id"),
        authorizationGuard = TaskAuthorizationGuardBean.class)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mandatory, as a real parent almost always is: a NOT NULL column is what proves the parent id
    // reaches the entity before the INSERT, not only as a follow-up UPDATE.
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private String title;

    private boolean done;
}
