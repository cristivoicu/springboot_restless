package ro.cristivoicu.springbootrestless.example.entity.project;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class ProjectSearchDto extends AbstractSearchDto {

    private String name;

    private String departmentCode;

    // No employeeId filter here: the default equality-filter does root.get(field.getName()),
    // and "employee" is a @ManyToOne association, not a flat column - filtering by assignee would
    // need a hand-written getSpecification() override, not added for this example yet.
}
