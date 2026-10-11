package ro.cristivoicu.springbootrestless.example.entity.shape;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessSubtype;

@Getter
@Setter
@RestlessSubtype(Square.class)
public class SquareModel extends ShapeModel {

    private Double side;
}
