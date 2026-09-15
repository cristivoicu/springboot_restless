package ro.cristivoicu.springbootrestless.example.entity.employee;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.cerbos.CerbosFieldMasker;
import ro.cristivoicu.springbootrestless.cerbos.CerbosPrincipalResolver;
import ro.cristivoicu.springbootrestless.cerbos.CerbosResourceAttributesMapper;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * Maps {@link Employee} to {@link EmployeeDto}, then masks {@link EmployeeDto#getSalary()} via
 * {@link CerbosFieldMasker} - a worked example of {@code @CerbosHiddenField}: {@code
 * policies/employee.yaml}'s {@code view} action hides {@code salary} for managers whose {@code
 * canViewSalary} JWT attribute isn't {@code true}, leaves it alone for admins.
 * <p>
 * {@link #map(List)} is overridden (not left to {@code Mapper}'s default per-element loop)
 * specifically to call {@link CerbosFieldMasker#maskAll} - one batched Cerbos RPC for the whole
 * list/page, instead of one {@code check()} per row.
 * <p>
 * This mapper is shared by both {@code EmployeeRestlessResource} (behind a Cerbos-backed guard,
 * on an authenticated-only route) <em>and</em> the older, deliberately-open Stage-0 {@code
 * EmployeeCreateController}/etc. at {@code /employees}. {@link CerbosPrincipalResolver#tryResolve()}
 * - not {@link CerbosPrincipalResolver#resolve()} - is used for exactly that reason: no
 * authenticated principal means no security is being enforced on this particular route at all, so
 * there's nothing to check against Cerbos either - the DTO passes through unmasked rather than
 * every response through the open route turning into a 401.
 */
@Component
public class EmployeeMapper implements Mapper<Employee, EmployeeDto> {

    /** Distinct from the CRUD actions {@code AuthorizationGuard} checks - see the class javadoc. */
    private static final String VIEW_ACTION = "view";
    private static final String RESOURCE_KIND = "employee";

    private final CerbosBlockingClient cerbosClient;

    public EmployeeMapper(CerbosBlockingClient cerbosClient) {
        this.cerbosClient = cerbosClient;
    }

    @Override
    public EmployeeDto map(Employee source) {
        EmployeeDto dto = toDto(source);
        return CerbosPrincipalResolver.tryResolve()
                .map(principal -> {
                    Resource resource = Resource.newInstance(RESOURCE_KIND, String.valueOf(source.getId()));
                    return CerbosFieldMasker.mask(cerbosClient, principal, resource, VIEW_ACTION, dto);
                })
                .orElse(dto);
    }

    @Override
    public List<EmployeeDto> map(List<Employee> source) {
        List<EmployeeDto> dtos = source.stream().map(this::toDto).toList();
        Optional<Principal> principal = CerbosPrincipalResolver.tryResolve();
        if (principal.isEmpty()) {
            return dtos;
        }
        return CerbosFieldMasker.maskAll(cerbosClient, principal.get(), RESOURCE_KIND, VIEW_ACTION, dtos,
                EmployeeDto::getId, CerbosResourceAttributesMapper.none());
    }

    private EmployeeDto toDto(Employee source) {
        return new EmployeeDto(source.getId(), source.getFirstName(), source.getLastName(),
                source.getEmail(), source.getSalary(), source.getDepartmentCode());
    }
}
