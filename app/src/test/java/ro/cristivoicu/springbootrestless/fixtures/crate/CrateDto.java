package ro.cristivoicu.springbootrestless.fixtures.crate;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class CrateDto implements EntityDto {
    private Long id;
    private String name;
    private String palletLabel;
}
