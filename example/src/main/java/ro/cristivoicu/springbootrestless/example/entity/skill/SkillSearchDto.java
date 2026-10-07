package ro.cristivoicu.springbootrestless.example.entity.skill;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class SkillSearchDto extends AbstractSearchDto {

    private String name;
}
