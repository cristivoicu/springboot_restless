package ro.cristivoicu.springbootrestless.fixtures.task;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class TaskCreateModel implements CreateModel {
    private String title;
    private String ownerUsername;
}
