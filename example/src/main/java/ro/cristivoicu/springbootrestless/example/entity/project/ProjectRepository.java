package ro.cristivoicu.springbootrestless.example.entity.project;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Hand-written now that {@link Project} is a manual resource (see {@code
 * ProjectRestlessResource}) rather than {@code @RestlessEntity}-generated - a missing repository
 * only gets generated alongside a generated resource.
 */
@Repository
public interface ProjectRepository extends SpecificationRepository<Project, Long> {
}
