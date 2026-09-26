package ro.cristivoicu.springbootrestless.example.entity.department;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.PatchModel;

/** Backs {@code PATCH /departments/{id}} - unlike {@link DepartmentUpdateModel}, both fields are nullable: {@code null} means "not sent," not "clear it." */
@Getter
@Setter
public class DepartmentPatchModel implements PatchModel {

    private String name;

    private String code;
}
