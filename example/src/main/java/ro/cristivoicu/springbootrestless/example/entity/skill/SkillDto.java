package ro.cristivoicu.springbootrestless.example.entity.skill;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.example.entity.shared.BaseEntity;
import ro.cristivoicu.springbootrestless.models.EntityDto;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SkillDto implements EntityDto {

    /** Round-trips as a UUID, not a {@code Long} - the generated repository/resource really did
     *  resolve {@link BaseEntity}'s inherited {@code @Id} type, not fall back to some default. */
    private UUID id;

    private String name;
}
