package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.beans.BeanUtils;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;
import ro.cristivoicu.springbootrestless.annotation.RestlessSubtype;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

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
        return specificationRepository.save(instantiate(createDto));
    }

    /**
     * Server-managed fields (a nested resource's parent, {@code @RestlessFromPrincipal}) are
     * written onto the entity <em>before</em> it's saved - see {@link RestlessContext#applyParentTo}
     * for why after-the-fact isn't enough for a NOT NULL column.
     */
    @Override
    public E create(C createDto, RestlessContext context) {
        E entity = instantiate(createDto);
        if (context != null) {
            context.applyServerFieldsTo(entity);
        }
        return specificationRepository.save(entity);
    }

    /**
     * The resource's entity type - or, for a polymorphic resource, the subclass the create model's
     * {@link RestlessSubtype} names (found on the model's class or any superclass).
     */
    private E instantiate(C createDto) {
        Class<?> target = entityType;
        RestlessSubtype subtype = org.springframework.core.annotation.AnnotationUtils.findAnnotation(createDto.getClass(), RestlessSubtype.class);
        if (subtype != null) {
            if (!entityType.isAssignableFrom(subtype.value())) {
                throw new IllegalStateException("@RestlessSubtype(" + subtype.value().getSimpleName() + ") on "
                        + createDto.getClass().getSimpleName() + " isn't a subtype of " + entityType.getSimpleName());
            }
            target = subtype.value();
        }
        @SuppressWarnings("unchecked")
        E entity = (E) BeanUtils.instantiateClass(target);
        BeanUtils.copyProperties(createDto, entity, ProtectedEntityFields.of(target));
        return entity;
    }

    @Override
    public Class<C> getDtoType() {
        return dtoType;
    }
}
