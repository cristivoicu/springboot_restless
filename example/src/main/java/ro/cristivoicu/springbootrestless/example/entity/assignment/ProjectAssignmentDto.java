package ro.cristivoicu.springbootrestless.example.entity.assignment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAssignmentDto implements EntityDto {
    private Long id;
    private Long projectId;
    private Long employeeId;
    private String departmentCode;
}
