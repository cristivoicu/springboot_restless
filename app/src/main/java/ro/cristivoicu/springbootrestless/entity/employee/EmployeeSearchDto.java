package ro.cristivoicu.springbootrestless.entity.employee;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class EmployeeSearchDto extends AbstractSearchDto {

    private String lastName;
}
