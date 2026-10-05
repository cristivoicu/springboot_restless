package ro.cristivoicu.springbootrestless.fixtures.nugget;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface NuggetRepository extends SpecificationRepository<Nugget, String> {
}
