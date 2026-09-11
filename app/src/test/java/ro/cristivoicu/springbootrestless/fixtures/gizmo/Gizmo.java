package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Test-only fixture (not shipped): exercises fully-default CUD via hand-wired
 * {@code Default*DataSource} instances - see {@code GizmoRestlessResource}. See
 * {@code example} module's original {@code Department} entity, which this fixture's role
 * replaced when entities moved out of {@code app}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Gizmo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String code;
}
