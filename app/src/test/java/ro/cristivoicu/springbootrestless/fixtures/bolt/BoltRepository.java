package ro.cristivoicu.springbootrestless.fixtures.bolt;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface BoltRepository extends SpecificationRepository<Bolt, Long> {
}
