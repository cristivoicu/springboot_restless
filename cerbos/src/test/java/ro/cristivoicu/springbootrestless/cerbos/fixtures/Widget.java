package ro.cristivoicu.springbootrestless.cerbos.fixtures;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal JPA fixture entity for this module's own tests - deliberately not reusing
 * {@code app}'s test-only {@code Gadget}/{@code Gizmo} fixtures, which live in {@code app}'s
 * {@code src/test} and aren't visible outside that module. No {@code @RestlessEntity}/dynamic
 * routing involved here: this module's translator and guard tests exercise {@link
 * org.springframework.data.jpa.domain.Specification} directly against a plain {@code
 * JpaSpecificationExecutor}, without going through {@code RestlessResourceHandler}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Widget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Long ownerId;

    private String department;
}
