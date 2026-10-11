package ro.cristivoicu.springbootrestless.example.entity.shape.store;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.example.entity.shape.Shape;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/** Outside the entity's package and off the naming convention - wired by {@code @RestlessEntity(repository = ...)}. */
@Repository
public interface ShapeStore extends SpecificationRepository<Shape, Long> {
}
