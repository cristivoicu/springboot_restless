package ro.cristivoicu.springbootrestless.example.entity.project;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

/**
 * No {@code employees}/{@code assignedEmployee} field here on purpose - a project's team can run
 * into the thousands, and embedding an unbounded list on every {@code GET /projects/{id}} is
 * exactly the anti-pattern {@code RestlessEmbed}'s own javadoc warns against. Who's on this
 * project is {@code GET /project-assignments?projectId={id}} instead - a real, paginated resource.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDto implements EntityDto {
    private Long id;
    private String name;
    private String description;
    private String departmentCode;
}
