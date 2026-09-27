package ro.cristivoicu.springbootrestless.example.entity.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.WriteActionRequest;

/** Request body for the {@code recordAchievement} write action - see {@link Achievement}'s own javadoc. */
@Getter
@Setter
public class RecordAchievementRequest implements WriteActionRequest {

    @NotBlank
    private String title;

    private int year;
}
