package ro.cristivoicu.springbootrestless.fixtures.racer;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class RacerCreateModel implements CreateModel {
    private String name;
}
