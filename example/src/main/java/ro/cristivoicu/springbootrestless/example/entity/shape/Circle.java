package ro.cristivoicu.springbootrestless.example.entity.shape;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@DiscriminatorValue("circle")
public class Circle extends Shape {

    private Double radius;
}
