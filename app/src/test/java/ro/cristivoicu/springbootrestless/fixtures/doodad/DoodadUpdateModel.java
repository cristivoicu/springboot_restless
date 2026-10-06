package ro.cristivoicu.springbootrestless.fixtures.doodad;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class DoodadUpdateModel implements UpdateModel {

    @NotBlank
    private String name;
}
