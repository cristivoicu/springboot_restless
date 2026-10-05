package ro.cristivoicu.springbootrestless.fixtures.crate;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

/** Reads {@code source.getPallet().getLabel()} - the lazy-association touch under test. */
@Component
public class CrateMapper implements Mapper<Crate, CrateDto> {
    @Override
    public CrateDto map(Crate source) {
        CrateDto dto = new CrateDto();
        dto.setId(source.getId());
        dto.setName(source.getName());
        dto.setPalletLabel(source.getPallet() == null ? null : source.getPallet().getLabel());
        return dto;
    }
}
