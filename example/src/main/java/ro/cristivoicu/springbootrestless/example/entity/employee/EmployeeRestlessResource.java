package ro.cristivoicu.springbootrestless.example.entity.employee;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.builders.AttributeValue;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosAuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosDtoResourceAttributesMapper;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.resource.ReadAction;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Map;

/**
 * Employee's real, single REST surface: hand-written create/read/update/delete data sources and
 * mapper (entity-specific logic, not the reflective defaults - see each {@code Employee*DataSource}
 * class), a named custom read action, and a real Cerbos-backed guard, all collapsed onto one
 * {@link RestlessResourceHandler} so its HTTP routes are registered dynamically rather than via
 * hand-written {@code @RestController} classes - the "manual: hand-wire a RestlessResourceHandler
 * subclass directly" tier the README's own tutorial describes, same branch {@code Department}
 * lives on, just with hand-written {@code *DataSource}s instead of {@code Default*DataSource}s.
 */
@Component
@RestlessResource(basePath = "/employees")
public class EmployeeRestlessResource extends RestlessResourceHandler<Employee, Long> {

    private final EmployeeCreateDataSource createDataSource;
    private final EmployeeReadDataSource readDataSource;
    private final EmployeeUpdateDataSource updateDataSource;
    private final EmployeeDeleteDataSource deleteDataSource;
    private final EmployeeMapper mapper;
    private final EmployeeContactMapper contactMapper;
    private final CerbosBlockingClient cerbosClient;

    public EmployeeRestlessResource(EmployeeCreateDataSource createDataSource,
                                     EmployeeReadDataSource readDataSource,
                                     EmployeeUpdateDataSource updateDataSource,
                                     EmployeeDeleteDataSource deleteDataSource,
                                     EmployeeMapper mapper,
                                     EmployeeContactMapper contactMapper,
                                     CerbosBlockingClient cerbosClient) {
        this.createDataSource = createDataSource;
        this.readDataSource = readDataSource;
        this.updateDataSource = updateDataSource;
        this.deleteDataSource = deleteDataSource;
        this.mapper = mapper;
        this.contactMapper = contactMapper;
        this.cerbosClient = cerbosClient;
    }

    @Override
    protected CreateDataSource<Employee, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Employee, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Employee, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Employee, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Employee, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Employee, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Employee, ?> getSelectMapper() {
        return mapper;
    }

    @Override
    protected Specification<Employee> getSpecification(SearchDto searchDto) {
        EmployeeSearchDto dto = (EmployeeSearchDto) searchDto;
        return (root, query, cb) -> StringUtils.hasText(dto.getLastName())
                ? cb.equal(root.get("lastName"), dto.getLastName())
                : cb.conjunction();
    }

    /**
     * Demonstrates a custom read action: a suffix {@code LIKE} on email domain, something the
     * default equality-match filter (used above for the main search) can't express.
     */
    @Override
    public Map<String, ReadAction<Employee, ?>> getCustomReadActions() {
        return Map.of("byEmailDomain", new ReadAction<Employee, EmployeeEmailDomainSearchDto>() {
            @Override
            public Class<EmployeeEmailDomainSearchDto> getSearchDtoType() {
                return EmployeeEmailDomainSearchDto.class;
            }

            @Override
            public Specification<Employee> buildSpecification(EmployeeEmailDomainSearchDto searchDto) {
                return (root, query, cb) -> StringUtils.hasText(searchDto.getDomain())
                        ? cb.like(root.get("email"), "%@" + searchDto.getDomain())
                        : cb.conjunction();
            }
        });
    }

    /**
     * The {@code contact} named view - a different bounded-context shape of the same aggregate
     * than {@link EmployeeDto}, at {@code GET /employees/{id}/contact}. See {@link
     * EmployeeContactDto}'s own javadoc.
     */
    @Override
    public Map<String, Mapper<Employee, ?>> getNamedViews() {
        return Map.of("contact", contactMapper);
    }

    /**
     * Real Cerbos-backed guard, replacing the earlier header-based stand-in: {@code
     * policies/employee.yaml} grants {@code admin} unconditional access, and scopes {@code
     * manager} to only the employees whose {@code lastName} matches their own {@code
     * scopedLastName} JWT claim - same semantics the old {@code X-Scope-LastName} header demo
     * had, now a real policy evaluated by a real PDP instead of hand-rolled guard code. See
     * {@code CerbosAuthorizationGuard}'s javadoc for how {@code preCheck}/{@code canAccess}/
     * {@code scope} map onto Cerbos's check/plan calls.
     * <p>
     * The DTO-aware constructor, not the plain entity-only one: {@code policies/employee.yaml}'s
     * {@code contact} action rule references {@code initials} - a field that only exists on a
     * mapped DTO (see {@link EmployeeContactDto#getInitials()}), never on {@link Employee} itself
     * - the worked example of {@link CerbosDtoResourceAttributesMapper}. {@link #contactMapper},
     * not {@link #mapper}, computes that DTO here specifically to avoid a second, wasted {@code
     * CerbosFieldMasker} round trip {@link EmployeeMapper#map} would otherwise trigger as a side
     * effect on every guard check - {@code contactMapper} does no Cerbos calls of its own, it's a
     * plain field copy plus the same {@code initials} computation.
     */
    @Override
    protected AuthorizationGuard<Employee> getAuthorizationGuard() {
        CerbosDtoResourceAttributesMapper<Employee, EmployeeContactDto> attributesMapper = (employee, dto) -> Map.of(
                "lastName", AttributeValue.stringValue(employee.getLastName()),
                "initials", AttributeValue.stringValue(dto.getInitials()));
        return new CerbosAuthorizationGuard<>(cerbosClient, "employee", Employee::getId, contactMapper, attributesMapper);
    }
}
