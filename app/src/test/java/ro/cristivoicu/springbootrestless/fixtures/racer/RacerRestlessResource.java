package ro.cristivoicu.springbootrestless.fixtures.racer;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;
import ro.cristivoicu.springbootrestless.resource.WriteAction;

import java.util.Map;

@Component
@RestlessResource(basePath = "/racers")
public class RacerRestlessResource extends RestlessResourceHandler<Racer, Long> {

    private final CreateDataSource<Racer, Long, RacerCreateModel> createDataSource;
    private final ReadDataSource<Racer, Long, RacerSearchDto> readDataSource;
    private final UpdateDataSource<Racer, Long, RacerUpdateModel> updateDataSource;
    private final DeleteDataSource<Racer, Long, ?> deleteDataSource;
    private final RacerMapper mapper;
    private final RacerAuthorizationGuard authorizationGuard = new RacerAuthorizationGuard();

    public RacerRestlessResource(RacerRepository repository, RacerMapper mapper) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Racer.class, RacerCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, RacerSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, RacerUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository, Long.class);
        this.mapper = mapper;
    }

    @Override
    protected CreateDataSource<Racer, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Racer, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Racer, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Racer, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Racer, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Racer, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Racer, ?> getSelectMapper() {
        return mapper;
    }

    @Override
    protected AuthorizationGuard<Racer> getAuthorizationGuard() {
        return authorizationGuard;
    }

    /** {@code "rename"} - see {@code WriteActionIfMatchTest} (Ground rules Phase 2 item 9: If-Match is now optionally honored here too). */
    @Override
    public Map<String, WriteAction<Racer, ?, ?>> getCustomWriteActions() {
        return Map.of("rename", new WriteAction<Racer, RacerRenameRequest, RacerDto>() {
            @Override
            public Class<RacerRenameRequest> getRequestType() {
                return RacerRenameRequest.class;
            }

            @Override
            public Class<RacerDto> getResponseType() {
                return RacerDto.class;
            }

            @Override
            public RacerDto execute(Racer entity, RacerRenameRequest request) {
                entity.setName(request.getNewName());
                return mapper.map(entity);
            }
        });
    }
}
