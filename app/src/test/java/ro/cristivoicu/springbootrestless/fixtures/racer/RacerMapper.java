package ro.cristivoicu.springbootrestless.fixtures.racer;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class RacerMapper implements Mapper<Racer, RacerDto> {
    @Override
    public RacerDto map(Racer source) {
        RacerDto dto = new RacerDto();
        BeanUtils.copyProperties(source, dto);
        return dto;
    }
}
