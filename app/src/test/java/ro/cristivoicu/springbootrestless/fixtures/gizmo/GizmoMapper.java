package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@Component
public class GizmoMapper implements Mapper<Gizmo, GizmoDto> {
    @Override
    public GizmoDto map(Gizmo source) {
        return new GizmoDto(source.getId(), source.getName(), source.getCode());
    }
}
