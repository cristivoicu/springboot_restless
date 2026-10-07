package ro.cristivoicu.springbootrestless.fixtures.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

import java.util.UUID;

/** Same deliberate {@code boardId} collision as {@link TicketCreateModel} - proves Feature B item 5: a full-replace PUT can never move a ticket to a different board, "protected like @Id". */
@Getter
@Setter
public class TicketUpdateModel implements UpdateModel {

    @NotBlank
    private String title;

    private UUID boardId;
}
