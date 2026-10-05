package ro.cristivoicu.springbootrestless.fixtures.nugget;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class NuggetCreateModel implements CreateModel {
    @NotBlank
    private String code;
    private String label;
}
