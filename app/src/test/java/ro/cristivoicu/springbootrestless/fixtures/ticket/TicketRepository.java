package ro.cristivoicu.springbootrestless.fixtures.ticket;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface TicketRepository extends SpecificationRepository<Ticket, Long> {
}
