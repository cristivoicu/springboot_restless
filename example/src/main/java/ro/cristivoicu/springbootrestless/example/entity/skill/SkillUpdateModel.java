package ro.cristivoicu.springbootrestless.example.entity.skill;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class SkillUpdateModel implements UpdateModel {

    @NotBlank
    private String name;
}
