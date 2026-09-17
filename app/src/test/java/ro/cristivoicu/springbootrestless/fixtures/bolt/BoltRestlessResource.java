package ro.cristivoicu.springbootrestless.fixtures.bolt;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Set;

/**
 * CREATE and READ_LIST only - see {@link Bolt}'s javadoc for why this fixture exists at all.
 */
@Component
@RestlessResource(basePath = "/bolts")
public class BoltRestlessResource extends RestlessResourceHandler<Bolt, Long> {

    private static final Set<AuthorizationGuard.Action> OPERATIONS = Set.of(
            AuthorizationGuard.Action.CREATE, AuthorizationGuard.Action.READ_LIST);

    private final BoltCreateDataSource createDataSource;
    private final ReadDataSource<Bolt, Long, BoltSearchDto> readDataSource;
    private final BoltMapper mapper;

    public BoltRestlessResource(BoltCreateDataSource createDataSource, BoltRepository repository, BoltMapper mapper) {
        this.createDataSource = createDataSource;
        this.readDataSource = new DefaultReadDataSource<>(repository, BoltSearchDto.class);
        this.mapper = mapper;
    }

    @Override
    protected CreateDataSource<Bolt, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Bolt, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected Mapper<Bolt, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Bolt, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Bolt, ?> getSelectMapper() {
        return mapper;
    }

    @Override
    public Set<AuthorizationGuard.Action> getEnabledOperations() {
        return OPERATIONS;
    }
}
