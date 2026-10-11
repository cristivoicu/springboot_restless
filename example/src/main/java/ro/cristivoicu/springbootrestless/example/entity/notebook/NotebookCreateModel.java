package ro.cristivoicu.springbootrestless.example.entity.notebook;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

/** No {@code ownerUsername}: it comes from the principal - the processor rejects a model that declares it. */
@Getter
@Setter
public class NotebookCreateModel implements CreateModel {

    @NotBlank
    private String title;

    private String code;
}
