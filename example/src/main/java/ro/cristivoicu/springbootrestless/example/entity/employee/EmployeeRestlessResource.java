package ro.cristivoicu.springbootrestless.example.entity.employee;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.builders.AttributeValue;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosAuthorizationGuard;
import ro.cristivoicu.springbootrestless.cerbos.CerbosDtoResourceAttributesMapper;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.filter.RestlessSpecifications;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.resource.ReadAction;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;
import ro.cristivoicu.springbootrestless.resource.WriteAction;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final EmployeeRepository repository;

    public EmployeeRestlessResource(EmployeeCreateDataSource createDataSource,
                                     EmployeeReadDataSource readDataSource,
                                     EmployeeUpdateDataSource updateDataSource,
                                     EmployeeDeleteDataSource deleteDataSource,
                                     EmployeeMapper mapper,
                                     EmployeeContactMapper contactMapper,
                                     CerbosBlockingClient cerbosClient,
                                     EmployeeRepository repository) {
        this.createDataSource = createDataSource;
        this.readDataSource = readDataSource;
        this.updateDataSource = updateDataSource;
        this.deleteDataSource = deleteDataSource;
        this.mapper = mapper;
        this.contactMapper = contactMapper;
        this.cerbosClient = cerbosClient;
        this.repository = repository;
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

    /**
     * Uses {@link ro.cristivoicu.springbootrestless.filter.RestlessSpecifications} to combine an
     * equality filter with a salary range - the point being demonstrated is that the builder
     * isn't only for the single-field case {@code GadgetRestlessResource} shows (see its own
     * javadoc): a hand-written override that needs more than plain equality (this one needs a
     * range too, since {@code EmployeeSearchDto}'s own {@code salaryGte}/{@code salaryLte} aren't
     * reachable by the reflection-driven default filter at all - {@code getSpecification} is
     * overridden here, not left to that default) is exactly where it earns its keep.
     */
    @Override
    protected Specification<Employee> getSpecification(SearchDto searchDto) {
        EmployeeSearchDto dto = (EmployeeSearchDto) searchDto;
        return RestlessSpecifications.<Employee>builder()
                .eq("lastName", dto.getLastName())
                .gte("salary", dto.getSalaryGte())
                .lte("salary", dto.getSalaryLte())
                .build();
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

    /** {@code giveRaise}'s cap - see its own javadoc below for why this is enforced by hand (409), not a bean-validation annotation (400). */
    private static final BigDecimal MAX_RAISE_PERCENTAGE = new BigDecimal("20");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Four named write actions - a full-replace {@code PUT} can express none of these
     * transitions correctly (see each action's own javadoc for exactly which invariant it, and
     * only it, enforces): {@code promote} (illegal-transition prevention, {@link JobTitle}'s own
     * javadoc), {@code giveRaise} (a business-rule cap a bean-validation annotation can't express
     * since it depends on the entity's own current state, not just the request body), {@code
     * addCertification}/{@code recordAchievement} (append-only mutation of a collection {@link
     * EmployeeUpdateModel} deliberately never exposes at all - see {@link Certification}'s own
     * javadoc). All four return the same masked {@link EmployeeDto} {@code update()} does, via
     * {@link #mapper} - an ordinary implementation detail of {@code execute}, not something
     * {@link WriteAction} itself requires (see its own javadoc).
     */
    @Override
    public Map<String, WriteAction<Employee, ?, ?>> getCustomWriteActions() {
        return Map.of(
                "promote", new WriteAction<Employee, PromoteRequest, EmployeeDto>() {
                    @Override
                    public Class<PromoteRequest> getRequestType() {
                        return PromoteRequest.class;
                    }

                    @Override
                    public Class<EmployeeDto> getResponseType() {
                        return EmployeeDto.class;
                    }

                    @Override
                    public EmployeeDto execute(Employee entity, PromoteRequest request) {
                        JobTitle newTitle = request.getNewJobTitle();
                        if (!newTitle.isPromotionFrom(entity.getJobTitle())) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "'" + newTitle + "' is not a promotion from '" + entity.getJobTitle() + "'");
                        }
                        entity.setJobTitle(newTitle);
                        return mapper.map(repository.save(entity));
                    }
                },

                "giveRaise", new WriteAction<Employee, GiveRaiseRequest, EmployeeDto>() {
                    @Override
                    public Class<GiveRaiseRequest> getRequestType() {
                        return GiveRaiseRequest.class;
                    }

                    @Override
                    public Class<EmployeeDto> getResponseType() {
                        return EmployeeDto.class;
                    }

                    /**
                     * {@code percentage > 0} is checked by {@link GiveRaiseRequest} itself
                     * (bean validation, 400 - a malformed request). {@code percentage <=
                     * MAX_RAISE_PERCENTAGE} is checked here instead (409 - a well-formed request
                     * that's against the rules) precisely because it depends on {@code
                     * MAX_RAISE_PERCENTAGE}, a rule about this entity/action, not a constraint
                     * bean validation could express against the request body alone.
                     */
                    @Override
                    public EmployeeDto execute(Employee entity, GiveRaiseRequest request) {
                        if (request.getPercentage().compareTo(MAX_RAISE_PERCENTAGE) > 0) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "A single raise cannot exceed " + MAX_RAISE_PERCENTAGE + "%");
                        }
                        BigDecimal current = entity.getSalary();
                        if (current == null) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Cannot give a raise - no salary currently on record");
                        }
                        BigDecimal factor = BigDecimal.ONE.add(request.getPercentage().divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
                        entity.setSalary(current.multiply(factor).setScale(2, RoundingMode.HALF_UP));
                        return mapper.map(repository.save(entity));
                    }
                },

                "addCertification", new WriteAction<Employee, AddCertificationRequest, EmployeeDto>() {
                    @Override
                    public Class<AddCertificationRequest> getRequestType() {
                        return AddCertificationRequest.class;
                    }

                    @Override
                    public Class<EmployeeDto> getResponseType() {
                        return EmployeeDto.class;
                    }

                    /** Rejects a duplicate name (case-insensitive) - the same "already-in-this-state" rule shape {@code giveRaise}'s cap demonstrates, on a collection instead of a scalar. */
                    @Override
                    public EmployeeDto execute(Employee entity, AddCertificationRequest request) {
                        boolean alreadyHeld = entity.getCertifications().stream()
                                .anyMatch(c -> c.getName().equalsIgnoreCase(request.getName()));
                        if (alreadyHeld) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "'" + request.getName() + "' is already on record");
                        }
                        entity.getCertifications().add(new Certification(request.getName(), request.getYearEarned()));
                        return mapper.map(repository.save(entity));
                    }
                },

                "recordAchievement", new WriteAction<Employee, RecordAchievementRequest, EmployeeDto>() {
                    @Override
                    public Class<RecordAchievementRequest> getRequestType() {
                        return RecordAchievementRequest.class;
                    }

                    @Override
                    public Class<EmployeeDto> getResponseType() {
                        return EmployeeDto.class;
                    }

                    @Override
                    public EmployeeDto execute(Employee entity, RecordAchievementRequest request) {
                        entity.getAchievements().add(new Achievement(request.getTitle(), request.getYear()));
                        return mapper.map(repository.save(entity));
                    }
                }
        );
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
