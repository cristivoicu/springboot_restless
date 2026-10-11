package ro.cristivoicu.springbootrestless.example.entity.attachment;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet;
import ro.cristivoicu.springbootrestless.annotation.RestlessDenial;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessOperation;
import ro.cristivoicu.springbootrestless.annotation.RestlessOwnedBy;
import ro.cristivoicu.springbootrestless.annotation.RestlessParent;
import ro.cristivoicu.springbootrestless.example.entity.notebook.Notebook;

/**
 * A file attached to a notebook - the Phase 4 demo, with no controller anywhere:
 * <ul>
 *   <li>upload is a multipart {@code POST} ({@code multipartCreate = true}) bound onto
 *       {@link AttachmentCreateModel#getFile()};</li>
 *   <li>the bytes come back through a named view returning a {@code RestlessBinary}
 *       ({@code GET .../attachments/{id}/content});</li>
 *   <li>{@code GET .../attachments/actions/total-size} is a collection action over exactly the
 *       rows the caller can see.</li>
 * </ul>
 * {@code collectionGet = LIST}: {@code GET .../attachments} is the plain array.
 * Owned via its notebook, like {@code Note}. No update route: files are replaced, not edited.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@RestlessEntity(basePath = "/notebooks/{notebookId}/attachments",
        parent = @RestlessParent(variable = "notebookId", field = "notebookId"),
        denyAs = RestlessDenial.NOT_FOUND,
        multipartCreate = true,
        collectionGet = RestlessCollectionGet.LIST,
        createDataSource = AttachmentCreateDataSource.class,
        views = AttachmentContentView.class,
        collectionActions = AttachmentTotalSizeAction.class,
        operations = {RestlessOperation.CREATE, RestlessOperation.READ_ONE, RestlessOperation.READ_LIST,
                RestlessOperation.DELETE_ONE, RestlessOperation.DELETE_ALL})
@RestlessOwnedBy(path = "notebookId", via = Notebook.class, ownerField = "ownerUsername")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long notebookId;

    private String filename;

    private String contentType;

    private long size;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;
}
