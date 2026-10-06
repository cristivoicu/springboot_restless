package ro.cristivoicu.springbootrestless.fixtures.doodad;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class DoodadCreateModel implements CreateModel {

    @NotBlank
    private String name;
}
