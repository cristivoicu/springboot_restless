package ro.cristivoicu.springbootrestless.fixtures.gadget;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.DeleteModel;

import java.util.List;

@Getter
@Setter
public class GadgetDeleteModel implements DeleteModel {

    @NotEmpty
    private List<String> ids;
}
