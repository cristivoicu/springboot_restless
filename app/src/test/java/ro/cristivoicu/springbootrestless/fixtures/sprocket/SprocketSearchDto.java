package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class SprocketSearchDto extends AbstractSearchDto {

    private String name;
}
