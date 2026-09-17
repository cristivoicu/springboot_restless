package ro.cristivoicu.springbootrestless.fixtures.bolt;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Test-only fixture (not shipped), existing purely to prove {@code
 * RestlessResourceHandler#createBulk} genuinely rolls back a partial write - see {@link
 * BoltCreateDataSource} (which deliberately saves, then sometimes throws) and {@code
 * BulkTransactionRollbackTest}. CREATE and READ_LIST only ({@code
 * BoltRestlessResource#getEnabledOperations}) - nothing else about this fixture is the point.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bolt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
