package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class GizmoMapper implements Mapper<Gizmo, GizmoDto> {
    @Override
    public GizmoDto map(Gizmo source) {
        // Setters, not the all-args constructor: `gadgets` is populated later, by
        // RestlessEmbedResolver, only when a caller asks for it via ?expand=gadgets.
        GizmoDto dto = new GizmoDto();
        dto.setId(source.getId());
        dto.setName(source.getName());
        dto.setCode(source.getCode());
        dto.setQuantity(source.getQuantity());
        return dto;
    }
}
