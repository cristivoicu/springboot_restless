package ro.cristivoicu.springbootrestless.example.entity.attachment;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessActionName;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessBinary;

/** {@code GET .../attachments/{id}/content}: the stored bytes, shown inline under their original name. */
@Component
@RestlessActionName("content")
public class AttachmentContentView implements Mapper<Attachment, RestlessBinary> {

    @Override
    public RestlessBinary map(Attachment source) {
        String type = source.getContentType() == null ? "application/octet-stream" : source.getContentType();
        return RestlessBinary.of(type, source.getData()).filename(source.getFilename()).inline();
    }
}
