package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

/** Request body for the {@code promote} write action - see {@link JobTitle}'s own javadoc. */
@Getter
@Setter
public class PromoteRequest implements WriteActionRequest {

    @NotNull
    private JobTitle newJobTitle;
}
