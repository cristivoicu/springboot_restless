package ro.cristivoicu.springbootrestless.example.entity.shape;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.example.entity.shape.store.ShapeStore;

/**
 * A polymorphic resource with no data source of its own: the create model's {@code kind} picks the
 * JSON subtype, and that subtype's {@code @RestlessSubtype} picks the entity subclass the default
 * create data source instantiates. One shared {@link ShapeModel} serves create and update - its
 * {@code name} is required only {@code OnCreate}. The repository lives in another package
 * ({@code repository = ShapeStore.class}). Deliberately unguarded demo data.
 */
@Entity
@Getter
@Setter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind")
@RestlessEntity(basePath = "/shapes", allowAll = true,
        createModel = ShapeModel.class, updateModel = ShapeModel.class,
        repository = ShapeStore.class)
public abstract class Shape {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
