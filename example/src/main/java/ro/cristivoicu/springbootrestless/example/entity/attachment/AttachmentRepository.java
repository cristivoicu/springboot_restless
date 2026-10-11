package ro.cristivoicu.springbootrestless.example.entity.attachment;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface AttachmentRepository extends SpecificationRepository<Attachment, Long> {
}
