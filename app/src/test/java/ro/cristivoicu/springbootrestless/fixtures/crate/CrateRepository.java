package ro.cristivoicu.springbootrestless.fixtures.crate;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface CrateRepository extends SpecificationRepository<Crate, Long> {
}
