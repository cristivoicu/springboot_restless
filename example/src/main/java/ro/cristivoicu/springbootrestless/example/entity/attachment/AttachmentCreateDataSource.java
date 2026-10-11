package ro.cristivoicu.springbootrestless.example.entity.attachment;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

/**
 * Turns the uploaded part into a row. Calls {@link RestlessContext#applyServerFieldsTo} before
 * saving so the NOT NULL {@code notebookId} is in the INSERT itself.
 */
@Component
public class AttachmentCreateDataSource extends CreateDataSource<Attachment, Long, AttachmentCreateModel> {

    private final AttachmentRepository repository;

    public AttachmentCreateDataSource(AttachmentRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public Attachment create(AttachmentCreateModel model, RestlessContext context) throws Exception {
        Attachment attachment = new Attachment();
        attachment.setFilename(model.getFile().getOriginalFilename());
        attachment.setContentType(model.getFile().getContentType());
        attachment.setSize(model.getFile().getSize());
        attachment.setData(model.getFile().getBytes());
        context.applyServerFieldsTo(attachment);
        return repository.save(attachment);
    }

    @Override
    public Attachment create(AttachmentCreateModel model) throws Exception {
        return create(model, RestlessContext.current());
    }
}
