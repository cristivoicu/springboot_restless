package ro.cristivoicu.springbootrestless.fixtures.crate;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class CrateCreateModel implements CreateModel {
    private String name;
    private Long palletId;
}
