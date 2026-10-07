package ro.cristivoicu.springbootrestless.fixtures.ticket;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

/** No {@code boardId} filter field - that scoping is the path's job (the parent filter is ANDed on automatically), not a client-supplied query parameter. */
@Getter
@Setter
public class TicketSearchDto extends AbstractSearchDto {

    private String title;
}
