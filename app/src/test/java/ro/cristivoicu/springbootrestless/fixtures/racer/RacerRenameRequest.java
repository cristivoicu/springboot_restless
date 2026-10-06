package ro.cristivoicu.springbootrestless.fixtures.racer;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

/** Request body for {@link RacerRestlessResource}'s {@code "rename"} write action - see {@code WriteActionIfMatchTest}. */
@Getter
@Setter
public class RacerRenameRequest implements WriteActionRequest {
    @NotBlank
    private String newName;
}
