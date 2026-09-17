package ro.cristivoicu.springbootrestless.fixtures.bolt;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class BoltMapper implements Mapper<Bolt, BoltDto> {
    @Override
    public BoltDto map(Bolt source) {
        return new BoltDto(source.getId(), source.getName());
    }
}
