package ro.cristivoicu.springbootrestless.example.entity.assignment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessOperation;

/**
 * The many-to-many between {@link ro.cristivoicu.springbootrestless.example.entity.project.Project}
 * and {@link ro.cristivoicu.springbootrestless.example.entity.employee.Employee}, a plain bridge
 * row rather than a JPA {@code @ManyToMany} on either entity - deliberately: {@code @ManyToMany}
 * makes Hibernate load and diff the <em>entire</em> collection on every write, has no pagination
 * of its own, and gives a project's team no ceiling at all (hundreds to tens of thousands of
 * rows is a completely ordinary team size for this kind of join, not an edge case). A bridge
 * entity exposed as its own {@code @RestlessEntity} resource instead gets real paging for free
 * ({@code GET /project-assignments?projectId={id}&page=0&size=50}) and a real bulk-write path
 * ({@code POST /project-assignments/bulk}, one request for many new rows) rather than needing a
 * dedicated protocol invented just for this relation.
 * <p>
 * Flat {@code projectId}/{@code employeeId} {@link Long}s, not {@code @ManyToOne} references
 * (unlike the short-lived {@code Project.employee} this replaced) - a pure junction row has no
 * reason to hydrate either side's full entity just to exist, and flat ids are exactly what {@link
 * ProjectAssignmentSearchDto}'s default equality filter (and {@code
 * RestlessResourceHandler#getSpecification}'s reflective default generally) already know how to
 * query without any hand-written {@code Specification}.
 * <p>
 * {@code departmentCode} is denormalized from the referenced {@link
 * ro.cristivoicu.springbootrestless.example.entity.project.Project} at creation time (see {@link
 * ProjectAssignmentCreateDataSource}) - the same natural-key-copy idiom {@code Project}/{@code
 * Employee} already use for their own {@code departmentCode}, here so {@link
 * ProjectAssignmentAuthorizationGuardBean} can row-scope membership visibility by department
 * without joining back to {@code Project} on every check.
 * <p>
 * {@code GenerationType.SEQUENCE} (not {@code IDENTITY}, unlike every other entity in this
 * codebase) - deliberately: {@code IDENTITY} forces Hibernate to insert (and round-trip for the
 * generated id) one row at a time, defeating JDBC batching entirely. A sequence with a matching
 * {@code allocationSize} lets Hibernate pre-fetch a block of ids and genuinely batch the inserts
 * {@link ProjectAssignmentCreateDataSource#createAll}'s {@code saveAll()} produces - see {@code
 * spring.jpa.properties.hibernate.jdbc.batch_size} in {@code application.properties}, set to the
 * same 50.
 * <p>
 * Generated tier, same as {@code Project} - {@code operations} excludes {@code UPDATE} (a
 * membership row is add/remove, never "edit in place"; see {@code ProjectAssignmentCreateModel}
 * for the create-only DTO shape this leaves) and {@code RestlessEntityProcessor} only requires a
 * {@code {Entity}UpdateModel} to resolve when {@code UPDATE} is actually in {@code operations} -
 * so excluding it here needs no phantom {@code ProjectAssignmentUpdateModel} nothing would ever
 * use, the same reasoning the old hand-written {@code ProjectAssignmentRestlessResource} spelled
 * out before this attribute existed.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"projectId", "employeeId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/project-assignments", createDataSource = ProjectAssignmentCreateDataSource.class,
        authorizationGuard = ProjectAssignmentAuthorizationGuardBean.class,
        operations = {RestlessOperation.CREATE, RestlessOperation.READ_ONE, RestlessOperation.READ_LIST,
                RestlessOperation.READ_PAGE, RestlessOperation.READ_PAGE_OVERVIEW, RestlessOperation.READ_PAGE_SELECT,
                RestlessOperation.DELETE_ONE, RestlessOperation.DELETE_ALL})
public class ProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_assignment_seq")
    @SequenceGenerator(name = "project_assignment_seq", sequenceName = "project_assignment_seq", allocationSize = 50)
    private Long id;

    private Long projectId;

    private Long employeeId;

    private String departmentCode;
}
