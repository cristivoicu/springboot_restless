package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class SprocketCreateModel implements CreateModel {

    @NotBlank
    private String name;

    private String description;
}
