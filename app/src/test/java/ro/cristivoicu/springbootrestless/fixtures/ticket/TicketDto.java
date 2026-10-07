package ro.cristivoicu.springbootrestless.fixtures.ticket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

import java.util.UUID;

/** Unlike example's TaskDto, boardId IS exposed here - purely so this fixture's own tests can assert on it directly without a second lookup. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto implements EntityDto {
    private Long id;
    private String title;
    private UUID boardId;
}
