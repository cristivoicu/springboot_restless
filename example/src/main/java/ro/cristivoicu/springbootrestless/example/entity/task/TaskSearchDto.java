package ro.cristivoicu.springbootrestless.example.entity.task;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

/** No {@code projectId}/{@code project} filter field - that scoping is the path's job (the parent filter is ANDed on automatically), not a client-supplied query parameter. */
@Getter
@Setter
public class TaskSearchDto extends AbstractSearchDto {

    private String title;

    private Boolean done;
}
