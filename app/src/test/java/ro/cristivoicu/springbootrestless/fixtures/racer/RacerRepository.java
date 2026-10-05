package ro.cristivoicu.springbootrestless.fixtures.racer;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface RacerRepository extends SpecificationRepository<Racer, Long> {
}
