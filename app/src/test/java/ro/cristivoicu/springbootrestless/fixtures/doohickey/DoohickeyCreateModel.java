package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class DoohickeyCreateModel implements CreateModel {

    @NotBlank
    private String name;

    private String secret;
}
