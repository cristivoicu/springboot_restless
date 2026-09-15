package ro.cristivoicu.springbootrestless.fixtures.cog;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class CogUpdateModel implements UpdateModel {

    private String name;

    private int teeth;
}
