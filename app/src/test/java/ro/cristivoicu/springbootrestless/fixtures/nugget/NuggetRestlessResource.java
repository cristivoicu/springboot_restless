package ro.cristivoicu.springbootrestless.fixtures.nugget;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Set;

/** Read-only beyond create: no update/delete needed for {@code RestlessEntityIdSortTest}. */
@Component
@RestlessResource(basePath = "/nuggets", allowAll = true)
public class NuggetRestlessResource extends RestlessResourceHandler<Nugget, String> {

    private final CreateDataSource<Nugget, String, NuggetCreateModel> createDataSource;
    private final ReadDataSource<Nugget, String, NuggetSearchDto> readDataSource;
    private final NuggetMapper mapper;

    public NuggetRestlessResource(NuggetCreateDataSource createDataSource, NuggetRepository repository, NuggetMapper mapper) {
        this.createDataSource = createDataSource;
        this.readDataSource = new DefaultReadDataSource<>(repository, NuggetSearchDto.class);
        this.mapper = mapper;
    }

    @Override
    public Set<AuthorizationGuard.Action> getEnabledOperations() {
        return Set.of(AuthorizationGuard.Action.CREATE, AuthorizationGuard.Action.READ_ONE,
                AuthorizationGuard.Action.READ_LIST, AuthorizationGuard.Action.READ_PAGE,
                AuthorizationGuard.Action.READ_PAGE_OVERVIEW, AuthorizationGuard.Action.READ_PAGE_SELECT);
    }

    @Override
    protected CreateDataSource<Nugget, String, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Nugget, String, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected Mapper<Nugget, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Nugget, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Nugget, ?> getSelectMapper() {
        return mapper;
    }
}
