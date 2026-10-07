package ro.cristivoicu.springbootrestless.fixtures.ticket;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessParent;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
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

/**
 * Manual-tier (hand-wired) nested resource, same framework shape every other {@code app}-module
 * fixture already uses - proves Feature B works with no processor/code generation involved at
 * all, exactly as {@code @RestlessResource(parent = ...)}'s own javadoc promises.
 */
@Component
@RestlessResource(basePath = "/boards/{boardId}/tickets", allowAll = true,
        parent = @RestlessParent(variable = "boardId", field = "boardId"))
public class TicketRestlessResource extends RestlessResourceHandler<Ticket, Long> {

    private final CreateDataSource<Ticket, Long, TicketCreateModel> createDataSource;
    private final ReadDataSource<Ticket, Long, TicketSearchDto> readDataSource;
    private final UpdateDataSource<Ticket, Long, TicketUpdateModel> updateDataSource;
    private final DeleteDataSource<Ticket, Long, ?> deleteDataSource;
    private final TicketMapper mapper;

    public TicketRestlessResource(TicketRepository repository, TicketMapper mapper) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Ticket.class, TicketCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, TicketSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, TicketUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository, Long.class);
        this.mapper = mapper;
    }

    @Override
    protected CreateDataSource<Ticket, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Ticket, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Ticket, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Ticket, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Ticket, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Ticket, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Ticket, ?> getSelectMapper() {
        return mapper;
    }
}
