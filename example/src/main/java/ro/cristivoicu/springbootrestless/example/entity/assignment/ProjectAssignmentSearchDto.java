package ro.cristivoicu.springbootrestless.example.entity.assignment;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

/**
 * Both filters are exactly what make this resource work as "who's on this project"/"which
 * projects is this person on" from either direction - {@code GET /project-assignments?projectId=X}
 * and {@code ?employeeId=Y} both fall out of {@code RestlessResourceHandler}'s default reflective
 * equality filter for free, no hand-written {@code Specification} needed.
 */
@Getter
@Setter
public class ProjectAssignmentSearchDto extends AbstractSearchDto {

    private Long projectId;

    private Long employeeId;
}
