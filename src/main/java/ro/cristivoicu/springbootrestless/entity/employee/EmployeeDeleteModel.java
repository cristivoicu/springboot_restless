package ro.cristivoicu.springbootrestless.entity.employee;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.DeleteModel;

import java.util.List;

@Getter
@Setter
public class EmployeeDeleteModel implements DeleteModel {

    @NotEmpty
    private List<Long> ids;
}
