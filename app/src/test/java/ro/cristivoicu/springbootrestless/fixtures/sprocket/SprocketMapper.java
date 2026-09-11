package ro.cristivoicu.springbootrestless.fixtures.sprocket;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class SprocketMapper implements Mapper<Sprocket, SprocketDto> {
    @Override
    public SprocketDto map(Sprocket source) {
        return new SprocketDto(source.getId(), source.getName(), source.getDescription());
    }
}
