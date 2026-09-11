package ro.cristivoicu.springbootrestless.fixtures.gadget;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class GadgetUpdateModel implements UpdateModel {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;
}
