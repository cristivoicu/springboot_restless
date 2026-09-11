package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.List;

/**
 * Default {@link ReadDataSource}: plain delegation to {@code specificationRepository} - every
 * hand-written {@code ReadDataSource} in this codebase has turned out to be exactly this, with
 * no entity-specific logic at all (search logic lives in {@code getSpecification}/{@code
 * ReadAction}, not here). Hand-write one instead only if an entity genuinely needs custom
 * fetch logic (e.g. an extra join, a native query).
 */
public class DefaultReadDataSource<E, K, R extends SearchDto>
        extends ReadDataSource<E, K, R> implements TypedDataSource<R> {

    private final Class<R> dtoType;

    public DefaultReadDataSource(SpecificationRepository<E, K> repository, Class<R> dtoType) {
        super(repository);
        this.dtoType = dtoType;
    }

    @Override
    public Page<E> findAll(Specification<E> specification, Pageable pageable) {
        return specificationRepository.findAll(specification, pageable);
    }

    @Override
    public List<E> findAll(Specification<E> specification) {
        return specificationRepository.findAll(specification);
    }

    @Override
    public E findOne(Specification<E> specification, K id) {
        return specificationRepository.findOne(specification)
                .filter(entity -> id.equals(idOf(entity)))
                .orElse(null);
    }

    @Override
    public E findOne(K id) {
        return specificationRepository.findById(id).orElse(null);
    }

    @Override
    public Class<R> getDtoType() {
        return dtoType;
    }

    // findOne(Specification, K) needs the entity's id to cross-check against - unlike
    // create/update/delete, a plain entity Class token isn't enough to extract that generically
    // without either a reflection-based id lookup or requiring entities to implement an
    // id-accessor contract. Reflection keeps this a true drop-in default (no new interface for
    // entities to implement); it's Specification#findOne's id-affirming branch only, not the hot
    // list/page path.
    @SuppressWarnings("unchecked")
    private K idOf(E entity) {
        try {
            // Walks the class hierarchy (not just getDeclaredFields() on the runtime class
            // alone), since @Id commonly lives on a shared @MappedSuperclass rather than on the
            // concrete entity itself.
            for (Class<?> type = entity.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                        field.setAccessible(true);
                        return (K) field.get(entity);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read @Id field of " + entity.getClass(), e);
        }
        throw new IllegalStateException(entity.getClass() + " has no @Id field");
    }
}
