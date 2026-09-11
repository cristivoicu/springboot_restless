package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface GadgetRepository extends SpecificationRepository<Gadget, Long> {
}
