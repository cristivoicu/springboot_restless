package ro.cristivoicu.springbootrestless.fixtures.rivet;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.PatchModel;

import java.time.Instant;

/**
 * Same deliberate shape as {@link RivetCreateModel}, boxed throughout (a {@code PatchModel}'s
 * fields must all be nullable - see its own javadoc) so "the client didn't send this" stays
 * expressible for every field, protected ones included.
 */
@Getter
@Setter
public class RivetPatchModel implements PatchModel {
    private String name;
    private Long id;
    private Long version;
    private Boolean deleted;
    private Instant createdDate;
    private Instant lastModifiedDate;
}
