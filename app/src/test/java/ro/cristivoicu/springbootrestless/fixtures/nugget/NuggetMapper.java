package ro.cristivoicu.springbootrestless.fixtures.nugget;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class NuggetMapper implements Mapper<Nugget, NuggetDto> {
    @Override
    public NuggetDto map(Nugget source) {
        NuggetDto dto = new NuggetDto();
        BeanUtils.copyProperties(source, dto);
        return dto;
    }
}
