package ro.cristivoicu.springbootrestless.example.entity.attachment;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessActionName;
import ro.cristivoicu.springbootrestless.resource.CollectionAction;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

import java.util.Map;

/** {@code GET .../attachments/actions/total-size}: count and bytes of the caller's visible attachments. */
@Component
@RestlessActionName("total-size")
public class AttachmentTotalSizeAction implements CollectionAction<Attachment> {

    private final AttachmentRepository repository;

    public AttachmentTotalSizeAction(AttachmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Object execute(Specification<Attachment> visibleRows, RestlessContext context) {
        var rows = repository.findAll(visibleRows);
        return Map.of("count", rows.size(), "bytes", rows.stream().mapToLong(Attachment::getSize).sum());
    }
}
