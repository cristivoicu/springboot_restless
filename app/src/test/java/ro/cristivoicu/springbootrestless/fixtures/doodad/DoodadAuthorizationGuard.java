package ro.cristivoicu.springbootrestless.fixtures.doodad;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

/**
 * Ground rules Phase 2 item 12 ("Fail-closed masking"): overrides {@link #postProcessResponse}
 * to uppercase {@link DoodadDto#getName()} - an arbitrary, easily observable transformation
 * standing in for a real masking policy, proving {@code RestlessResourceHandler} actually invokes
 * this hook (and with the right DTO) on every single-entity response, not just that the
 * interface's own default compiles.
 */
@Component
public class DoodadAuthorizationGuard implements AuthorizationGuard<Doodad> {

    @Override
    public <D> D postProcessResponse(Action action, String customActionName, HttpServletRequest request, Doodad entity, D dto) {
        if (dto instanceof DoodadDto doodadDto && doodadDto.getName() != null) {
            doodadDto.setName(doodadDto.getName().toUpperCase());
        }
        return dto;
    }
}
