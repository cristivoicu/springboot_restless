package ro.cristivoicu.springbootrestless.fixtures.bolt;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class BoltCreateModel implements CreateModel {

    @NotBlank
    private String name;
}
