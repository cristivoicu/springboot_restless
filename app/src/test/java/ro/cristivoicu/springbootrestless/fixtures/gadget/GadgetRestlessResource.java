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
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.resource.ReadAction;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Map;

/**
 * Test-only fixture: the same hand-written data sources {@link GadgetCreateController} etc. use,
 * collapsed onto one {@link RestlessResourceHandler} at {@code /gadgets-dynamic} - the parity
 * baseline for the dynamic mechanism, plus a named custom read action and an authorization
 * guard demo (both opt-in framework features).
 */
@Component
@RestlessResource(basePath = "/gadgets-dynamic")
public class GadgetRestlessResource extends RestlessResourceHandler<Gadget, Long> {

    private final GadgetCreateDataSource createDataSource;
    private final GadgetReadDataSource readDataSource;
    private final GadgetUpdateDataSource updateDataSource;
    private final GadgetDeleteDataSource deleteDataSource;
    private final GadgetMapper mapper;

    public GadgetRestlessResource(GadgetCreateDataSource createDataSource,
                                   GadgetReadDataSource readDataSource,
                                   GadgetUpdateDataSource updateDataSource,
                                   GadgetDeleteDataSource deleteDataSource,
                                   GadgetMapper mapper) {
        this.createDataSource = createDataSource;
        this.readDataSource = readDataSource;
        this.updateDataSource = updateDataSource;
        this.deleteDataSource = deleteDataSource;
        this.mapper = mapper;
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

    @Override
    protected Specification<Gadget> getSpecification(SearchDto searchDto) {
        GadgetSearchDto dto = (GadgetSearchDto) searchDto;
        return (root, query, cb) -> StringUtils.hasText(dto.getLastName())
                ? cb.equal(root.get("lastName"), dto.getLastName())
                : cb.conjunction();
    }

    /**
     * Demonstrates a custom read action: a suffix {@code LIKE} on email domain, something the
     * default equality-match filter (used above for the main search) can't express.
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
                return (root, query, cb) -> StringUtils.hasText(searchDto.getDomain())
                        ? cb.like(root.get("email"), "%@" + searchDto.getDomain())
                        : cb.conjunction();
            }
        });
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
