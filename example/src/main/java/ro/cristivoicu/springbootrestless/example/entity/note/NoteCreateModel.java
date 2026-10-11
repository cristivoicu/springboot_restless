package ro.cristivoicu.springbootrestless.example.entity.note;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class NoteCreateModel implements CreateModel {

    @NotBlank
    private String text;
}
