package ro.cristivoicu.springbootrestless.example.entity.assignment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

/**
 * No {@code departmentCode} here - it's derived from {@link #projectId} by {@link
 * ProjectAssignmentCreateDataSource}, never taken from the caller (a client-supplied department
 * could otherwise desynchronize it from the project's own, silently breaking {@link
 * ProjectAssignmentAuthorizationGuardBean}'s row-scoping).
 */
@Getter
@Setter
public class ProjectAssignmentCreateModel implements CreateModel {

    @NotNull
    private Long projectId;

    @NotNull
    private Long employeeId;
}
