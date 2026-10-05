package ro.cristivoicu.springbootrestless.fixtures.task;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class TaskUpdateModel implements UpdateModel {
    private String title;
    private String ownerUsername;
}
