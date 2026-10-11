package ro.cristivoicu.springbootrestless.example.entity.notebook;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

/** {@code ?ownerUsername=} is accepted but can't widen anything: the ownership scope is ANDed on top. */
@Getter
@Setter
public class NotebookSearchDto extends AbstractSearchDto {

    private String title;

    private String ownerUsername;
}
