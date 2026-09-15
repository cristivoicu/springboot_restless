package ro.cristivoicu.springbootrestless.cerbos.fixtures;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.cristivoicu.springbootrestless.cerbos.CerbosHiddenField;

/**
 * Fixture DTO for {@code CerbosFieldMaskerTest}: {@code name} masks under its own field name,
 * {@code secret} masks under an explicit custom key, {@code id} is never annotated (control), and
 * {@code flag} is a primitive annotated field - specifically to prove masking it throws.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MaskableDto {

    private Long id;

    @CerbosHiddenField
    private String name;

    @CerbosHiddenField("secretKey")
    private String secret;

    @CerbosHiddenField
    private boolean flag;
}
