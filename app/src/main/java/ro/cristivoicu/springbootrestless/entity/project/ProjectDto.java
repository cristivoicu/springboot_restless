package ro.cristivoicu.springbootrestless.entity.project;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDto implements EntityDto {
    private Long id;
    private String name;
    private String description;
}
