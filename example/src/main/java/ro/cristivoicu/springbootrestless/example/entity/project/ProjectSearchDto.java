package ro.cristivoicu.springbootrestless.example.entity.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

import java.time.Instant;

@Getter
@Setter
public class ProjectSearchDto extends AbstractSearchDto {

    private String name;

    private String departmentCode;

    // No employeeId filter here: the default equality-filter does root.get(field.getName()),
    // and "employee" is a @ManyToOne association, not a flat column - filtering by assignee would
    // need a hand-written getSpecification() override, not added for this example yet.

    /**
     * Filter-DSL demo, both fields: {@code createdDate} is declared on {@link
     * ro.cristivoicu.springbootrestless.models.AbstractAuditableEntity} (a shared {@code
     * @MappedSuperclass}, not directly on {@link Project} itself) - reachable here only because
     * {@code RestlessResourceHandler#entityPropertyNames} walks the whole class hierarchy, not
     * just {@code Project}'s own declared fields. No hand-written {@code getSpecification()}
     * needed at all: {@code GET /projects?createdDateGte=2026-01-01T00:00:00Z} falls straight out
     * of the reflection-driven default filter, the same way {@code name}/{@code departmentCode}
     * above already do for plain equality.
     */
    @Schema(description = "Only projects created at or after this instant.", example = "2026-01-01T00:00:00Z")
    private Instant createdDateGte;

    @Schema(description = "Only projects created at or before this instant.", example = "2026-12-31T23:59:59Z")
    private Instant createdDateLte;
}
