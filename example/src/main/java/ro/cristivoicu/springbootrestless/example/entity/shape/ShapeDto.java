package ro.cristivoicu.springbootrestless.example.entity.shape;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class ShapeDto implements EntityDto {

    private Long id;
    private String name;
}
