package ro.cristivoicu.springbootrestless.fixtures.cog;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class CogSearchDto extends AbstractSearchDto {

    private String name;
}
