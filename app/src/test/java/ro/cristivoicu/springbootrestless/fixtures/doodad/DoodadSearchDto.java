package ro.cristivoicu.springbootrestless.fixtures.doodad;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class DoodadSearchDto extends AbstractSearchDto {

    private String name;
}
