package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class SprocketUpdateModel implements UpdateModel {

    @NotBlank
    private String name;

    private String description;
}
