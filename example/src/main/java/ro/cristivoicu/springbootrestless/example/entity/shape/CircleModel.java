package ro.cristivoicu.springbootrestless.example.entity.shape;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessSubtype;

@Getter
@Setter
@RestlessSubtype(Circle.class)
public class CircleModel extends ShapeModel {

    private Double radius;
}
