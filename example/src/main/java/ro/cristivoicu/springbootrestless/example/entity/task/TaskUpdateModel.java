package ro.cristivoicu.springbootrestless.example.entity.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

/** No {@code projectId}/{@code project} field here either - a full-replace PUT can never move a task to a different project, see {@code RestlessParent}'s own "protected like @Id" semantics. */
@Getter
@Setter
public class TaskUpdateModel implements UpdateModel {

    @NotBlank
    private String title;

    private boolean done;
}
