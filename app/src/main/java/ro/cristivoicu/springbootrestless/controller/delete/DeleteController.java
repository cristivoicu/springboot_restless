package ro.cristivoicu.springbootrestless.controller.delete;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.DeleteModel;

public abstract class DeleteController<E, K, D extends DeleteModel> implements ro.cristivoicu.springbootrestless.error.RestlessErrorScope {
    protected final DeleteDataSource<E, K, D> dataSource;

    protected DeleteController(DeleteDataSource<E, K, D> dataSource) {
        this.dataSource = dataSource;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteById(@PathVariable K id) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        dataSource.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // POST, not DELETE-with-a-body - see RestlessRoutes#FIXED's identical "deleteAll" entry for
    // the full RFC 9110 reasoning; this hand-subclassed tier matches the dynamic mechanism's
    // route shape exactly, on purpose (ErrorResponseParityTest-style parity, not just for CUD).
    @PostMapping("/bulk-delete")
    public ResponseEntity<?> delete(@RequestBody D entity) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        dataSource.deleteAll(entity);
        return ResponseEntity.noContent().build();
    }

    protected abstract Mapper<E, ?> getEntityMapper();

}
