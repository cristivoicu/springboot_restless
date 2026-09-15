package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class DoohickeySearchDto extends AbstractSearchDto {

    private String name;
}
