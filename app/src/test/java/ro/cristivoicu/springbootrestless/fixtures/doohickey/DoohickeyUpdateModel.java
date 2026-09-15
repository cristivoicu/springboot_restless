package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class DoohickeyUpdateModel implements UpdateModel {

    @NotBlank
    private String name;

    private String secret;
}
