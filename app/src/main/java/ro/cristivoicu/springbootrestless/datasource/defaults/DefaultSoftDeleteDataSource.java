package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.SoftDeletable;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Sibling to {@link DefaultDeleteDataSource} for an {@code E} implementing {@link
 * SoftDeletable}: flags a row ({@code setDeleted(true)} + {@code save()}) instead of removing it.
 * Same constructor shape and {@link DefaultDeleteModel} DTO as {@link DefaultDeleteDataSource} -
 * point {@code @RestlessEntity(deleteDataSource = ...)} or a hand-wired resource's {@code
 * getDeleteDataSource()} at this instead, and nothing else about an entity's setup changes. See
 * {@link DefaultDeleteDataSource}'s own javadoc for why a three-arg, {@link ConversionService}-
 * accepting constructor exists alongside the two-arg one (Ground rules Phase 2 item 10).
 * <p>
 * A row already flagged deleted is flagged again, idempotently - not treated as "already gone"
 * the way a hard {@code deleteById} on a missing row already silently no-ops.
 */
public class DefaultSoftDeleteDataSource<E extends SoftDeletable, K>
        extends DeleteDataSource<E, K, DefaultDeleteModel> implements TypedDataSource<DefaultDeleteModel> {

    private final Class<K> idType;
    private final ConversionService conversionService;

    public DefaultSoftDeleteDataSource(SpecificationRepository<E, K> repository, Class<K> idType) {
        this(repository, idType, DefaultConversionService.getSharedInstance());
    }

    public DefaultSoftDeleteDataSource(SpecificationRepository<E, K> repository, Class<K> idType, ConversionService conversionService) {
        super(repository);
        this.idType = idType;
        this.conversionService = conversionService;
    }

    @Override
    public void deleteById(K id) {
        specificationRepository.findById(id).ifPresent(this::flagDeleted);
    }

    @Override
    public void deleteAll(DefaultDeleteModel d) {
        d.getIds().forEach(rawId -> {
            K id = conversionService.convert(rawId, idType);
            specificationRepository.findById(id).ifPresent(this::flagDeleted);
        });
    }

    private void flagDeleted(E entity) {
        entity.setDeleted(true);
        specificationRepository.save(entity);
    }

    @Override
    public Class<DefaultDeleteModel> getDtoType() {
        return DefaultDeleteModel.class;
    }
}
