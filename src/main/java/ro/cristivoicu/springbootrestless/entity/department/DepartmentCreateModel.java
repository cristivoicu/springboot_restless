package ro.cristivoicu.springbootrestless.entity.department;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class DepartmentCreateModel implements CreateModel {

    @NotBlank
    private String name;

    @NotBlank
    private String code;
}
