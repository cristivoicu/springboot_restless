package ro.cristivoicu.springbootrestless.fixtures.task;

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

@Component
@RestlessResource(basePath = "/tasks")
public class TaskRestlessResource extends RestlessResourceHandler<Task, Long> {

    private final CreateDataSource<Task, Long, TaskCreateModel> createDataSource;
    private final ReadDataSource<Task, Long, TaskSearchDto> readDataSource;
    private final UpdateDataSource<Task, Long, TaskUpdateModel> updateDataSource;
    private final DeleteDataSource<Task, Long, ?> deleteDataSource;
    private final TaskMapper mapper;
    private final TaskAuthorizationGuard authorizationGuard = new TaskAuthorizationGuard();

    public TaskRestlessResource(TaskRepository repository, TaskMapper mapper) {
        this.createDataSource = new DefaultCreateDataSource<>(repository, Task.class, TaskCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, TaskSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, TaskUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository, Long.class);
        this.mapper = mapper;
    }

    @Override
    protected CreateDataSource<Task, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Task, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Task, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Task, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Task, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Task, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Task, ?> getSelectMapper() {
        return mapper;
    }

    @Override
    protected AuthorizationGuard<Task> getAuthorizationGuard() {
        return authorizationGuard;
    }
}
