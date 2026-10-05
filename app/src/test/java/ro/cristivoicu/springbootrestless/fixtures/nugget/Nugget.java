package ro.cristivoicu.springbootrestless.fixtures.nugget;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Natural-key fixture: {@code @Id} is {@code code}, not literally named {@code "id"} - proves
 * {@code RestlessResourceHandler#pageableOf} resolves the entity's real {@code @Id} property for
 * its default sort (Ground rules item 4) instead of {@code AbstractSearchDto}'s hardcoded {@code
 * "id"} literal, which this entity has no field matching at all. Before that fix, every
 * unsorted request to an entity shaped like this got a 400 ("Unknown sort property 'id'").
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nugget {

    @Id
    private String code;

    private String label;
}
