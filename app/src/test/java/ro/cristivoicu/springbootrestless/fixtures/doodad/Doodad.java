package ro.cristivoicu.springbootrestless.fixtures.doodad;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

/**
 * Ground rules Phase 2 item 12 ("Fail-closed masking"): dedicated fixture proving {@code
 * RestlessResourceHandler} actually calls {@link AuthorizationGuard#postProcessResponse}, wired
 * through {@link DoodadAuthorizationGuard}, on every single-entity response path (create,
 * findOne, update) - not just that the default no-op exists. A separate entity from {@code
 * Doohickey} on purpose, so this guard's response-transforming override can't be mistaken for
 * (or interact with) any of {@code Doohickey}'s own, unrelated fixture behavior.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/doodads", authorizationGuard = DoodadAuthorizationGuard.class)
public class Doodad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
