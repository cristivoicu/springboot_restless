package ro.cristivoicu.springbootrestless.example.entity.note;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class NoteUpdateModel implements UpdateModel {

    @NotBlank
    private String text;
}
