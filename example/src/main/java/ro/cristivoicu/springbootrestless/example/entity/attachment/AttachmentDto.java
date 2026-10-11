package ro.cristivoicu.springbootrestless.example.entity.attachment;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

/** Metadata only - the bytes are served by the {@code content} view. */
@Getter
@Setter
public class AttachmentDto implements EntityDto {

    private Long id;
    private Long notebookId;
    private String filename;
    private String contentType;
    private long size;
}
