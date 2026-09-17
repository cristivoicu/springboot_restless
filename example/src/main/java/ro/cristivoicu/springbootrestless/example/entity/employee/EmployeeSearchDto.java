package ro.cristivoicu.springbootrestless.example.entity.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class EmployeeSearchDto extends AbstractSearchDto {

    @Schema(description = "Exact-match filter on last name.", example = "Lovelace")
    private String lastName;
}
