package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.PatchModel;

/**
 * Every field optional and unvalidated (no {@code @NotBlank}) - unlike {@link
 * DoohickeyUpdateModel}, {@code null} here means "the client didn't send this field", not "clear
 * it". See {@code PatchDataSource}'s javadoc.
 */
@Getter
@Setter
public class DoohickeyPatchModel implements PatchModel {
    private String name;
    private String secret;
}
