package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.beans.BeanUtils;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Default {@link CreateDataSource}: builds a new entity instance and copies whichever
 * matching-named properties the {@code CreateModel} declares onto it via
 * {@link BeanUtils#copyProperties}, then saves. Covers the common "no custom logic" case —
 * hand-write a {@code CreateDataSource} subclass instead when creation needs anything beyond
 * a straight field copy (computed values, related-entity lookups, audit fields, ...).
 * <p>
 * Requires {@code E} to have a no-arg constructor (a plain JPA entity already needs one).
 * <p>
 * Instantiated directly (not subclassed), so its generic type arguments are erased at the
 * instance level — implements {@link TypedDataSource} to supply its DTO type explicitly instead
 * of relying on {@code GenericTypeResolver} against a concrete subclass.
 */
public class DefaultCreateDataSource<E, K, C extends CreateModel>
        extends CreateDataSource<E, K, C> implements TypedDataSource<C> {

    private final Class<E> entityType;
    private final Class<C> dtoType;

    public DefaultCreateDataSource(SpecificationRepository<E, K> repository, Class<E> entityType, Class<C> dtoType) {
        super(repository);
        this.entityType = entityType;
        this.dtoType = dtoType;
    }

    @Override
    public E create(C createDto) {
        E entity = BeanUtils.instantiate(entityType);
        BeanUtils.copyProperties(createDto, entity, ProtectedEntityFields.of(entityType));
        return specificationRepository.save(entity);
    }

    @Override
    public Class<C> getDtoType() {
        return dtoType;
    }
}
