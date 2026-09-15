package ro.cristivoicu.springbootrestless.fixtures.cog;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CogDto implements EntityDto {
    private Long id;
    private String name;
    private int teeth;
}
