package ro.cristivoicu.springbootrestless.example.entity.skill;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class SkillCreateModel implements CreateModel {

    @NotBlank
    private String name;
}
