package ro.cristivoicu.springbootrestless.example.entity.shape;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.models.RestlessValidationGroups;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

/** Create and update body of {@code /shapes}; {@code kind} chooses the subtype. */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CircleModel.class, name = "circle"),
        @JsonSubTypes.Type(value = SquareModel.class, name = "square"),
})
public abstract class ShapeModel implements CreateModel, UpdateModel {

    /** Required to create a shape; an update may leave it out. */
    @NotBlank(groups = RestlessValidationGroups.OnCreate.class)
    private String name;
}
