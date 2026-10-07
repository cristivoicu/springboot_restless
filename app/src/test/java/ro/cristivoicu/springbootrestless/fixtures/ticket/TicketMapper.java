package ro.cristivoicu.springbootrestless.fixtures.ticket;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class TicketMapper implements Mapper<Ticket, TicketDto> {
    @Override
    public TicketDto map(Ticket source) {
        TicketDto dto = new TicketDto();
        dto.setId(source.getId());
        dto.setTitle(source.getTitle());
        dto.setBoardId(source.getBoardId());
        return dto;
    }
}
