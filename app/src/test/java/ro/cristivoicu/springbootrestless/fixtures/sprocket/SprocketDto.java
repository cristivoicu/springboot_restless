package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SprocketDto implements EntityDto {
    private Long id;
    private String name;
    private String description;
}
