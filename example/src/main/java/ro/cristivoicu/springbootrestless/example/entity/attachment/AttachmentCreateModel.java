package ro.cristivoicu.springbootrestless.example.entity.attachment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import ro.cristivoicu.springbootrestless.models.CreateModel;

/** Bound from {@code multipart/form-data}: the part named {@code file}. */
@Getter
@Setter
public class AttachmentCreateModel implements CreateModel {

    @NotNull
    private MultipartFile file;
}
