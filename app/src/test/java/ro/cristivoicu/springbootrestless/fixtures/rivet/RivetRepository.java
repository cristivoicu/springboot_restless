package ro.cristivoicu.springbootrestless.fixtures.rivet;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface RivetRepository extends SpecificationRepository<Rivet, Long> {
}
