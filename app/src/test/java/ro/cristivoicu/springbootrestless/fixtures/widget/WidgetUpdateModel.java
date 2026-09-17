package ro.cristivoicu.springbootrestless.fixtures.widget;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

@Getter
@Setter
public class WidgetUpdateModel implements UpdateModel {
    private String name;
}
