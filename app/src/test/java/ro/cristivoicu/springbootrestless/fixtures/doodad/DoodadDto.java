package ro.cristivoicu.springbootrestless.fixtures.doodad;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
public class DoodadDto implements EntityDto {
    private Long id;
    private String name;
}
