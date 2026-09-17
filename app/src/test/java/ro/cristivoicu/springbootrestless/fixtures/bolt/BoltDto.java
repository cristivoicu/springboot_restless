package ro.cristivoicu.springbootrestless.fixtures.bolt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoltDto implements EntityDto {
    private Long id;
    private String name;
}
