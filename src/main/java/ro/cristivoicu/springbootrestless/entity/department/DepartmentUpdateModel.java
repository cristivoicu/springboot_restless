package ro.cristivoicu.springbootrestless.entity.department;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class DepartmentUpdateModel implements UpdateModel {

    @NotBlank
    private String name;

    @NotBlank
    private String code;
}
