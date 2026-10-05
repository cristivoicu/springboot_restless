package ro.cristivoicu.springbootrestless.fixtures.task;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface TaskRepository extends SpecificationRepository<Task, Long> {
}
