package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

import java.math.BigDecimal;

/**
 * Request body for the {@code giveRaise} write action. {@code percentage} being positive is a
 * request-shape check (bean validation, 400) - the separate rule that it can't exceed {@link
 * EmployeeRestlessResource}'s own cap is a business rule, enforced by hand inside {@code execute}
 * as a 409, not by an annotation here. Two different kinds of "invalid" deliberately kept apart:
 * a malformed request vs. one that's well-formed but against the rules.
 */
@Getter
@Setter
public class GiveRaiseRequest implements WriteActionRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "must be a positive percentage")
    private BigDecimal percentage;
}
