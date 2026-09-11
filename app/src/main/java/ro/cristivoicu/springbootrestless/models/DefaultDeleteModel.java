package ro.cristivoicu.springbootrestless.models;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Ready-made {@link DeleteModel} for entities that don't need a bespoke bulk-delete shape —
 * pair with {@code DefaultDeleteDataSource} so most entities need no delete-side files at all.
 */
@Getter
@Setter
public class DefaultDeleteModel implements DeleteModel {

    @NotEmpty
    private List<String> ids;
}
