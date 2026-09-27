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
 * {@link CerbosPrincipalResolver#tryResolve()} - not {@link CerbosPrincipalResolver#resolve()} -
 * is used defensively: {@code /employees} itself is authenticated-only (see {@code SecurityConfig}),
 * so a missing principal shouldn't happen in practice, but a mapper has no business throwing on it
 * either - an absent principal means the DTO passes through unmasked rather than every call site
 * that maps an {@link Employee} needing to handle a resolution failure.
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
        // Setters, not the all-args constructor: `projects`/`department` are populated later, by
        // RestlessEmbedResolver, only when a caller asks for them via ?expand=.
        EmployeeDto dto = new EmployeeDto();
        dto.setId(source.getId());
        dto.setVersion(source.getVersion());
        dto.setFirstName(source.getFirstName());
        dto.setLastName(source.getLastName());
        dto.setEmail(source.getEmail());
        dto.setInitials(initialsOf(source));
        dto.setSalary(source.getSalary());
        dto.setDepartmentCode(source.getDepartmentCode());
        dto.setJobTitle(source.getJobTitle());
        dto.setCertifications(source.getCertifications());
        dto.setAchievements(source.getAchievements());
        return dto;
    }

    /** See {@link EmployeeDto#getInitials()}'s own javadoc - the worked example of a DTO-only computed attribute. */
    private static String initialsOf(Employee source) {
        String first = initialOf(source.getFirstName());
        String last = initialOf(source.getLastName());
        return first + last;
    }

    private static String initialOf(String name) {
        return name == null || name.isBlank() ? "" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }
}
