package ro.cristivoicu.springbootrestless.controller.update;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.UpdateModel;

/** {@code R} is a real type parameter, not {@code ?} - same reasoning as {@code CreateController}'s own javadoc. */
public abstract class UpdateController<E, K, U extends UpdateModel, R> implements ro.cristivoicu.springbootrestless.error.RestlessErrorScope {
    protected final UpdateDataSource<E, K, U> dataSource;

    protected UpdateController(UpdateDataSource<E, K, U> dataSource) {
        this.dataSource = dataSource;
    }

    protected abstract Mapper<E, R> getEntityMapper();

    @PutMapping("/{id}")
    public ResponseEntity<R> update(@PathVariable K id, @Validated @RequestBody U updateDto) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        return ResponseEntity.ok().body(
                getEntityMapper().map(dataSource.update(id, updateDto))
        );
    }

}
