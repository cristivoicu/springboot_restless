package ro.cristivoicu.springbootrestless.fixtures.nugget;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class NuggetDto implements EntityDto {
    private String code;
    private String label;
}
