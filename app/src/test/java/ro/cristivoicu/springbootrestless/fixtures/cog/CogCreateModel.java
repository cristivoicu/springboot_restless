package ro.cristivoicu.springbootrestless.fixtures.cog;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class CogCreateModel implements CreateModel {

    private String name;

    private int teeth;
}
