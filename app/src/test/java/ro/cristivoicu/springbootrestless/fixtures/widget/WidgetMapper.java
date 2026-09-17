package ro.cristivoicu.springbootrestless.fixtures.widget;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class WidgetMapper implements Mapper<Widget, WidgetDto> {
    @Override
    public WidgetDto map(Widget source) {
        return new WidgetDto(source.getId(), source.getName(), source.getVersion());
    }
}
