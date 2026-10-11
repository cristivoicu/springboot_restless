package ro.cristivoicu.springbootrestless.example.entity.note;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessBulkMode;
import ro.cristivoicu.springbootrestless.annotation.RestlessDenial;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessOwnedBy;
import ro.cristivoicu.springbootrestless.annotation.RestlessParent;
import ro.cristivoicu.springbootrestless.example.entity.notebook.Notebook;

/**
 * Ownership "via a parent", nested under it: a note belongs to whoever owns its notebook. The
 * guard answers for the notebook in the path once per request ({@code preCheck}); {@code
 * @RestlessParent} keeps every row under that notebook. A flat scalar NOT NULL parent column -
 * the shape most schemas use.
 * <p>
 * Bulk create is {@code PARTIAL}: a batch of notes (an import, say) keeps its good rows, reports
 * the invalid ones, and skips a note whose text already exists in the notebook.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@RestlessEntity(basePath = "/notebooks/{notebookId}/notes",
        parent = @RestlessParent(variable = "notebookId", field = "notebookId"),
        denyAs = RestlessDenial.NOT_FOUND,
        bulkMode = RestlessBulkMode.PARTIAL, bulkDedupe = NoteTextKey.class)
@RestlessOwnedBy(path = "notebookId", via = Notebook.class, ownerField = "ownerUsername")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long notebookId;

    private String text;
}
