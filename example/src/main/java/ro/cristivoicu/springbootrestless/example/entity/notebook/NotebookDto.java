package ro.cristivoicu.springbootrestless.example.entity.notebook;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class NotebookDto implements EntityDto {

    private Long id;
    private String title;
    private String code;
    private String ownerUsername;
}
