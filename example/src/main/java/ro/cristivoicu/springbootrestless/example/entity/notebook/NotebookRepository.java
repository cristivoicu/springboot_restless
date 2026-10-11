package ro.cristivoicu.springbootrestless.example.entity.notebook;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface NotebookRepository extends SpecificationRepository<Notebook, Long> {
}
