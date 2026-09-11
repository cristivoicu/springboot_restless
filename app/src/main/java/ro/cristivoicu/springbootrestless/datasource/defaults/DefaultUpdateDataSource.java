package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Default {@link UpdateDataSource}: loads the entity by id, copies whichever matching-named
 * properties the {@code UpdateModel} declares onto it via {@link BeanUtils#copyProperties},
 * then saves. See {@link DefaultCreateDataSource} for when to hand-write one instead.
 */
public class DefaultUpdateDataSource<E, K, U extends UpdateModel>
        extends UpdateDataSource<E, K, U> implements TypedDataSource<U> {

    private final Class<U> dtoType;

    public DefaultUpdateDataSource(SpecificationRepository<E, K> repository, Class<U> dtoType) {
        super(repository);
        this.dtoType = dtoType;
    }

    @Override
    public E update(K id, U updateDto) {
        E entity = specificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity " + id + " not found"));
        BeanUtils.copyProperties(updateDto, entity);
        return specificationRepository.save(entity);
    }

    @Override
    public Class<U> getDtoType() {
        return dtoType;
    }
}
