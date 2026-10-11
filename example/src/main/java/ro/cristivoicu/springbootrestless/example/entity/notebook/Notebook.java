package ro.cristivoicu.springbootrestless.example.entity.notebook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessDenial;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessFromPrincipal;
import ro.cristivoicu.springbootrestless.annotation.RestlessImmutable;
import ro.cristivoicu.springbootrestless.annotation.RestlessOwnedBy;

/**
 * This codebase's demo of declarative ownership: every notebook belongs to whoever created it, and
 * to nobody else. No guard class, no data source - the annotations are the whole policy:
 * <ul>
 *   <li>{@code @RestlessFromPrincipal ownerUsername} - set from the caller on create, never from a
 *       body, never changed afterwards (and NOT NULL, so it must reach the INSERT itself);</li>
 *   <li>{@code @RestlessOwnedBy(field = "ownerUsername")} - lists contain only the caller's
 *       notebooks, every single-row route checks the owner;</li>
 *   <li>{@code denyAs = NOT_FOUND} - someone else's notebook answers exactly like a missing one;</li>
 *   <li>{@code @RestlessImmutable(onUpdate = REJECT) code} - settable on create, a 400 if an update
 *       tries to change it.</li>
 * </ul>
 * {@code Note} shows the "via a parent" shape underneath it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@RestlessEntity(basePath = "/notebooks", denyAs = RestlessDenial.NOT_FOUND,
        collectionActions = NotebookStatsAction.class, writeActions = NotebookStampAction.class,
        hooks = NotebookAuditHook.class)
@RestlessOwnedBy(field = "ownerUsername")
public class Notebook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @RestlessImmutable(onUpdate = RestlessImmutable.OnChange.REJECT)
    private String code;

    @RestlessFromPrincipal
    @Column(nullable = false)
    private String ownerUsername;
}
