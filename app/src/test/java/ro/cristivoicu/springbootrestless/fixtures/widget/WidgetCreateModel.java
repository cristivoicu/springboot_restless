package ro.cristivoicu.springbootrestless.fixtures.widget;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.CreateModel;

@Getter
@Setter
public class WidgetCreateModel implements CreateModel {
    private String name;
}
