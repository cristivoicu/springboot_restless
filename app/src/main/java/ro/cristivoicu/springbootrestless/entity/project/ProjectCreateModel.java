package ro.cristivoicu.springbootrestless.entity.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class ProjectCreateModel implements CreateModel {

    @NotBlank
    private String name;

    private String description;
}
