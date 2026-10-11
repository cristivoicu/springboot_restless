package ro.cristivoicu.springbootrestless.example.entity.note;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class NoteDto implements EntityDto {

    private Long id;
    private Long notebookId;
    private String text;
}
