package ro.cristivoicu.springbootrestless.example.entity.skill;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.example.entity.shared.BaseEntity;

/**
 * Codeless tier, on purpose the simplest possible entity in this app: nothing here is about
 * authorization (unlike every sibling entity, which wires a real {@code CerbosAuthorizationGuard}
 * - see each one's own javadoc) - {@code allowAll = true} is the deliberate choice, since the one
 * thing this fixture exists to demonstrate is {@link BaseEntity}'s inherited {@code @Id} resolving
 * correctly through the generated tier end to end, not authorization. See {@link BaseEntity}'s own
 * javadoc for the full reasoning.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@RestlessEntity(basePath = "/skills", allowAll = true)
public class Skill extends BaseEntity {

    private String name;
}
