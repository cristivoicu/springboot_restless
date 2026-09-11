package ro.cristivoicu.springbootrestless.entity.department;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class DepartmentSearchDto extends AbstractSearchDto {

    private String name;
}
