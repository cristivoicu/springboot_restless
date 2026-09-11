package ro.cristivoicu.springbootrestless.fixtures.gadget;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GadgetDto implements EntityDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
}
