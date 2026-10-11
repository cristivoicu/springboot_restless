package ro.cristivoicu.springbootrestless.example.entity.notebook;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

/** {@code code} is here on purpose: sending the stored value is fine, sending a different one is a 400. */
@Getter
@Setter
public class NotebookUpdateModel implements UpdateModel {

    @NotBlank
    private String title;

    private String code;
}
