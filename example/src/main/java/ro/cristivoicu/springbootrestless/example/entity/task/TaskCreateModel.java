package ro.cristivoicu.springbootrestless.example.entity.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

/**
 * No {@code projectId}/{@code project} field here at all - which project a task belongs to comes
 * from the path ({@code POST /projects/{projectId}/tasks}), never the body. The mass-assignment-
 * style collision case (a body-supplied value for the exact same field the path already injects)
 * is covered separately by an {@code app}-module unit test using a scalar parent field, since
 * {@code Task}'s own parent field is a dotted association, not a same-named scalar.
 */
@Getter
@Setter
public class TaskCreateModel implements CreateModel {

    @NotBlank
    private String title;
}
