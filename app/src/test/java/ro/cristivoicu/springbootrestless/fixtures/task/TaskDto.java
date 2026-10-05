package ro.cristivoicu.springbootrestless.fixtures.task;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.EntityDto;

@Getter
@Setter
public class TaskDto implements EntityDto {
    private Long id;
    private String title;
    private String ownerUsername;
}
