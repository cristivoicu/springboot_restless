package ro.cristivoicu.springbootrestless.fixtures.racer;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class RacerDto implements EntityDto {
    private Long id;
    private String name;
    private Long version;
}
