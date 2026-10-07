package ro.cristivoicu.springbootrestless.fixtures.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

import java.util.UUID;

/**
 * {@code boardId} is here deliberately, same name as {@link Ticket#getBoardId()} - proves
 * Feature B item 2: even though {@code DefaultCreateDataSource}'s reflective copy would happily
 * write a client-controlled {@code boardId} straight onto the entity (nothing on the manual tier
 * protects against that by name the way the processor's mass-assignment check does for
 * {@code @Id}/{@code @Version}), {@code RestlessResourceHandler} forcibly overwrites it with the
 * path's value right after create runs - the body's value is silently never honored.
 */
@Getter
@Setter
public class TicketCreateModel implements CreateModel {

    @NotBlank
    private String title;

    private UUID boardId;
}
