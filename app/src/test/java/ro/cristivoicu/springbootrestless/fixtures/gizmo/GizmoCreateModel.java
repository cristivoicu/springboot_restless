package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class GizmoCreateModel implements CreateModel {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    /** Optional - unset means "no quantity recorded yet". */
    private Integer quantity;
}
