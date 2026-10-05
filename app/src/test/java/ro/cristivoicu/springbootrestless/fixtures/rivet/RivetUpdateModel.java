package ro.cristivoicu.springbootrestless.fixtures.rivet;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

import java.time.Instant;

/** Same deliberate shape as {@link RivetCreateModel} - see its javadoc. */
@Getter
@Setter
public class RivetUpdateModel implements UpdateModel {
    private String name;
    private Long id;
    private Long version;
    private boolean deleted;
    private Instant createdDate;
    private Instant lastModifiedDate;
}
