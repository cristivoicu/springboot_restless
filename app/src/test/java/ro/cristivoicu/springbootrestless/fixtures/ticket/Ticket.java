package ro.cristivoicu.springbootrestless.fixtures.ticket;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Test-only fixture: the <em>scalar</em>-FK form of a nested ({@code @RestlessParent}) resource -
 * {@code boardId} is a flat {@code UUID} column directly on the entity, the same shape
 * backend-nunti's own real integration needed ({@code Guest.eventId}). See {@code example}'s
 * {@code Task} for the other form (a dotted {@code @ManyToOne} association).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID boardId;

    private String title;
}
