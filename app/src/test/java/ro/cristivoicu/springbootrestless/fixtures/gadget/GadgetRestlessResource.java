package ro.cristivoicu.springbootrestless.fixtures.gadget;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
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

import java.util.Map;

/**
 * Test-only fixture: the same hand-written data sources {@link GadgetCreateController} etc. use,
 * collapsed onto one {@link RestlessResourceHandler} at {@code /gadgets-dynamic} - the parity
 * baseline for the dynamic mechanism, plus a named custom read action, a named write action, and
 * an authorization guard demo (all opt-in framework features).
 */
@Component
@RestlessResource(basePath = "/gadgets-dynamic")
public class GadgetRestlessResource extends RestlessResourceHandler<Gadget, Long> {

    private final GadgetCreateDataSource createDataSource;
    private final GadgetReadDataSource readDataSource;
    private final GadgetUpdateDataSource updateDataSource;
    private final GadgetDeleteDataSource deleteDataSource;
    private final GadgetMapper mapper;
    private final GadgetRepository repository;

    public GadgetRestlessResource(GadgetCreateDataSource createDataSource,
                                   GadgetReadDataSource readDataSource,
                                   GadgetUpdateDataSource updateDataSource,
                                   GadgetDeleteDataSource deleteDataSource,
                                   GadgetMapper mapper,
                                   GadgetRepository repository) {
        this.createDataSource = createDataSource;
        this.readDataSource = readDataSource;
        this.updateDataSource = updateDataSource;
        this.deleteDataSource = deleteDataSource;
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    protected CreateDataSource<Gadget, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Gadget, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Gadget, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Gadget, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Gadget, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Gadget, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Gadget, ?> getSelectMapper() {
        return mapper;
    }

    /**
     * Uses {@link RestlessSpecifications} instead of a hand-written lambda - proof it's a genuine
     * drop-in for exactly this shape (a single optional equality filter), not just new-code sugar.
     * Byte-for-byte equivalent to the original hand-written version: {@code
     * RestlessSpecifications#eq} already treats a blank/null value as absent, the same
     * {@code StringUtils.hasText} check the original ternary did by hand.
     */
    @Override
    protected Specification<Gadget> getSpecification(SearchDto searchDto) {
        GadgetSearchDto dto = (GadgetSearchDto) searchDto;
        return RestlessSpecifications.<Gadget>builder()
                .eq("lastName", dto.getLastName())
                .build();
    }

    /**
     * Demonstrates a custom read action: a suffix {@code LIKE} on email domain, something the
     * default equality-match filter (used above for the main search) can't express. Also uses
     * {@link RestlessSpecifications} - the domain still has to be blank-checked before the {@code
     * "%@" + domain} pattern is built (a blank domain concatenated first would produce the
     * non-absent, bogus pattern {@code "%@"} instead of being treated as unset).
     */
    @Override
    public Map<String, ReadAction<Gadget, ?>> getCustomReadActions() {
        return Map.of("byEmailDomain", new ReadAction<Gadget, GadgetEmailDomainSearchDto>() {
            @Override
            public Class<GadgetEmailDomainSearchDto> getSearchDtoType() {
                return GadgetEmailDomainSearchDto.class;
            }

            @Override
            public Specification<Gadget> buildSpecification(GadgetEmailDomainSearchDto searchDto) {
                String pattern = StringUtils.hasText(searchDto.getDomain()) ? "%@" + searchDto.getDomain() : null;
                return RestlessSpecifications.<Gadget>builder()
                        .like("email", pattern)
                        .build();
            }
        });
    }

    /**
     * Demonstrates a named write action: an intent-carrying rename, distinct from the
     * full-replace {@code update()} - the action author decides what to mutate and what shape to
     * return ({@link GadgetDto}, via the same {@link #mapper} {@code getEntityMapper()} already
     * uses, called here as an ordinary implementation detail of {@code execute} - {@link
     * WriteAction} itself never requires a {@code Mapper}, see its own javadoc).
     */
    @Override
    public Map<String, WriteAction<Gadget, ?, ?>> getCustomWriteActions() {
        return Map.of("rename", new WriteAction<Gadget, GadgetRenameRequest, GadgetDto>() {
            @Override
            public Class<GadgetRenameRequest> getRequestType() {
                return GadgetRenameRequest.class;
            }

            @Override
            public Class<GadgetDto> getResponseType() {
                return GadgetDto.class;
            }

            @Override
            public GadgetDto execute(Gadget entity, GadgetRenameRequest request) {
                entity.setLastName(request.getNewLastName());
                return mapper.map(repository.save(entity));
            }
        });
    }

    /**
     * Demonstrates a named view: an alternate shape of the same entity beyond the default
     * {@code findOne}, keyed by name. Reuses {@link GadgetDto} itself (a second, distinct DTO
     * would be overkill for a demo) - the point being exercised is the dispatch mechanism, not a
     * genuinely different projection. Drive-by fix: {@code getNamedViews()}/{@code namedView(...)}
     * had zero test coverage anywhere in the reactor before this - the only real usage was
     * {@code example}'s {@code EmployeeRestlessResource}, never asserted in a test.
     */
    @Override
    public Map<String, Mapper<Gadget, ?>> getNamedViews() {
        return Map.of("summary", mapper);
    }

    /**
     * Demonstration guard: a stand-in for a real principal-derived guard. No-op (fully open)
     * when the {@value #SCOPE_HEADER} header is absent, so every test that doesn't send this
     * header is unaffected. When present, scopes list/page reads to {@code lastName = <header
     * value>} and denies direct fetch/update/delete of any entity whose {@code lastName} doesn't
     * match.
     */
    private static final String SCOPE_HEADER = "X-Scope-LastName";

    @Override
    protected AuthorizationGuard<Gadget> getAuthorizationGuard() {
        return new AuthorizationGuard<>() {
            @Override
            public Specification<Gadget> scope(Action action, String customActionName, HttpServletRequest request) {
                String scopedLastName = request.getHeader(SCOPE_HEADER);
                if (!StringUtils.hasText(scopedLastName)) {
                    return null;
                }
                return (root, query, cb) -> cb.equal(root.get("lastName"), scopedLastName);
            }

            @Override
            public boolean canAccess(Action action, HttpServletRequest request, Gadget entity) {
                String scopedLastName = request.getHeader(SCOPE_HEADER);
                if (!StringUtils.hasText(scopedLastName)) {
                    return true;
                }
                return scopedLastName.equals(entity.getLastName());
            }
        };
    }
}
