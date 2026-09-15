package ro.cristivoicu.springbootrestless.example.entity.project;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class ProjectSearchDto extends AbstractSearchDto {

    private String name;

    private String departmentCode;
}
