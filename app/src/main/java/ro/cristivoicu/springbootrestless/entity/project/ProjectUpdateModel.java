package ro.cristivoicu.springbootrestless.entity.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class ProjectUpdateModel implements UpdateModel {

    @NotBlank
    private String name;

    private String description;
}
