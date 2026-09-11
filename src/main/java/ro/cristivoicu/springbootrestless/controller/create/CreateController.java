package ro.cristivoicu.springbootrestless.controller.create;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.CreateModel;

public abstract class CreateController<E, K, C extends CreateModel> {
    protected final CreateDataSource<E, K, C> dataSource;

    protected CreateController(CreateDataSource<E, K, C> dataSource) {
        this.dataSource = dataSource;
    }

    protected abstract Mapper<E, ?> getEntityMapper();

    @PostMapping
    public ResponseEntity<?> create(@Validated @RequestBody C createDto) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        return ResponseEntity.ok().body(
                getEntityMapper().map(dataSource.create(createDto))
        );
    }

}
