package ro.cristivoicu.springbootrestless.fixtures.gadget;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

/**
 * Test-only fixture: the request body for {@link GadgetRestlessResource}'s {@code "rename"}
 * write action - demonstrates the mechanism with the smallest possible transition (just
 * {@code lastName}), same spirit as {@code GadgetEmailDomainSearchDto} being the smallest
 * possible custom read action demo.
 */
@Getter
@Setter
public class GadgetRenameRequest implements WriteActionRequest {

    @NotBlank
    private String newLastName;
}
