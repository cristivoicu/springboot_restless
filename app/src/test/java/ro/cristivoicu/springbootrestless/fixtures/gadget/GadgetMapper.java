package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class GadgetMapper implements Mapper<Gadget, GadgetDto> {
    @Override
    public GadgetDto map(Gadget source) {
        return new GadgetDto(source.getId(), source.getFirstName(), source.getLastName(), source.getEmail());
    }
}
