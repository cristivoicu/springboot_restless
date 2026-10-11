package ro.cristivoicu.springbootrestless.example.entity.shape;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@DiscriminatorValue("square")
public class Square extends Shape {

    private Double side;
}
