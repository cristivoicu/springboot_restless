package ro.cristivoicu.springbootrestless.fixtures.gadget;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Test-only fixture (not shipped): exercises hand-written Create/Read/Update/Delete
 * DataSources, hand-written {@code @RestController} classes at {@code /gadgets} (the
 * parity-testing baseline), a dynamic {@code GadgetRestlessResource} at
 * {@code /gadgets-dynamic}, a custom read action, and the authorization guard. See the
 * {@code example} module's original {@code Employee} entity, which this fixture's role
 * replaced when entities moved out of {@code app}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Gadget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    private String email;
}
