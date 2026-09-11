package ro.cristivoicu.springbootrestless.controller.update;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

public abstract class UpdateController<E, K, U extends UpdateModel> {
    protected final UpdateDataSource<E, K, U> dataSource;

    protected UpdateController(UpdateDataSource<E, K, U> dataSource) {
        this.dataSource = dataSource;
    }

    protected abstract Mapper<E, ?> getEntityMapper();

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable K id, @Validated @RequestBody U updateDto) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        return ResponseEntity.ok().body(
                getEntityMapper().map(dataSource.update(id, updateDto))
        );
    }

}
