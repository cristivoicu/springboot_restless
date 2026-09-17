package ro.cristivoicu.springbootrestless.fixtures.widget;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.annotation.RestlessResource;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultCreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultSoftDeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultUpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

/**
 * Manual tier (same shape as {@code Department}'s own resource in {@code example}): every verb is
 * a {@code Default*DataSource}, wired by hand instead of by the processor - here specifically so
 * {@link DefaultSoftDeleteDataSource} (not part of the processor's naming-convention/attribute
 * defaulting yet) can be wired directly with no extra delegating-bean ceremony. No {@code
 * getAuthorizationGuard()} override - stays default-permissive, which matters for this fixture:
 * with no guard configured, {@code RestlessResourceHandler} only loads an entity before
 * update/patch/delete when an {@code If-Match} header is present (see its own {@code hasGuard() ||
 * ifMatch != null} condition) - proving that path works independently of authorization.
 */
@Component
@RestlessResource(basePath = "/widgets")
public class WidgetRestlessResource extends RestlessResourceHandler<Widget, Long> {

    private final CreateDataSource<Widget, Long, WidgetCreateModel> createDataSource;
    private final ReadDataSource<Widget, Long, WidgetSearchDto> readDataSource;
    private final UpdateDataSource<Widget, Long, WidgetUpdateModel> updateDataSource;
    private final DeleteDataSource<Widget, Long, ?> deleteDataSource;
    private final WidgetMapper mapper;

    public WidgetRestlessResource(WidgetRepository repository, WidgetMapper mapper) {
        this.mapper = mapper;
        this.createDataSource = new DefaultCreateDataSource<>(repository, Widget.class, WidgetCreateModel.class);
        this.readDataSource = new DefaultReadDataSource<>(repository, WidgetSearchDto.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, WidgetUpdateModel.class);
        this.deleteDataSource = new DefaultSoftDeleteDataSource<>(repository, Long.class);
    }

    @Override
    protected CreateDataSource<Widget, Long, ?> getCreateDataSource() {
        return createDataSource;
    }

    @Override
    protected ReadDataSource<Widget, Long, ?> getReadDataSource() {
        return readDataSource;
    }

    @Override
    protected UpdateDataSource<Widget, Long, ?> getUpdateDataSource() {
        return updateDataSource;
    }

    @Override
    protected DeleteDataSource<Widget, Long, ?> getDeleteDataSource() {
        return deleteDataSource;
    }

    @Override
    protected Mapper<Widget, ?> getEntityMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Widget, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Widget, ?> getSelectMapper() {
        return mapper;
    }
}
