package ro.cristivoicu.springbootrestless.fixtures.gadget;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class GadgetSearchDto extends AbstractSearchDto {

    private String lastName;
}
