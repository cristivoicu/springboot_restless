package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

@Getter
@Setter
public class GizmoSearchDto extends AbstractSearchDto {

    private String name;
}
