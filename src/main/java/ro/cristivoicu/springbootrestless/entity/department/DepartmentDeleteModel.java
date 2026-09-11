package ro.cristivoicu.springbootrestless.entity.department;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.DeleteModel;

import java.util.List;

@Getter
@Setter
public class DepartmentDeleteModel implements DeleteModel {

    @NotEmpty
    private List<Long> ids;
}
