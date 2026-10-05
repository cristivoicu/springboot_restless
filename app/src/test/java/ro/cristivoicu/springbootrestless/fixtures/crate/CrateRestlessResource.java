package ro.cristivoicu.springbootrestless.fixtures.crate;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Set;

@Component
@RestlessResource(basePath = "/crates", allowAll = true)
public class CrateRestlessResource extends RestlessResourceHandler<Crate, Long> {

    private final CrateCreateDataSource createDataSource;
    private final ReadDataSource<Crate, Long, CrateSearchDto> readDataSource;
    private final CrateMapper mapper;

    public CrateRestlessResource(CrateCreateDataSource createDataSource, CrateRepository repository, CrateMapper mapper) {
        this.createDataSource = createDataSource;
        this.readDataSource = new DefaultReadDataSource<>(repository, CrateSearchDto.class);
        this.mapper = mapper;
    }

    @Override
    public Set<AuthorizationGuard.Action> getEnabledOperations() {
        return Set.of(AuthorizationGuard.Action.CREATE, AuthorizationGuard.Action.READ_ONE,
                AuthorizationGuard.Action.READ_LIST, AuthorizationGuard.Action.READ_PAGE,
                AuthorizationGuard.Action.READ_PAGE_OVERVIEW, AuthorizationGuard.Action.READ_PAGE_SELECT);
    }

    @Override
    protected CreateDataSource<Crate, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Crate, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected Mapper<Crate, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Crate, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Crate, ?> getSelectMapper() {
        return mapper;
    }
}
