package ro.cristivoicu.springbootrestless.example.entity.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

/** No {@code projectId} field - the client already knows it, it's in the URL they called. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto implements EntityDto {
    private Long id;
    private String title;
    private boolean done;
}
