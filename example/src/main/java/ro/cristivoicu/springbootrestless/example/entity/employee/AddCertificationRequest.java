package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

/** Request body for the {@code addCertification} write action - see {@link Certification}'s own javadoc. */
@Getter
@Setter
public class AddCertificationRequest implements WriteActionRequest {

    @NotBlank
    private String name;

    private int yearEarned;
}
