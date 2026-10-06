package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Default {@link DeleteDataSource}: deletes by id, and bulk-deletes via {@link DefaultDeleteModel}
 * (fixed — the delete DTO shape is always just "a list of ids", so there's no reason for most
 * entities to declare their own).
 * <p>
 * Ground rules Phase 2 item 10: the two-arg constructor converts ids via a standalone, shared
 * {@link DefaultConversionService} - a custom {@link org.springframework.core.convert.converter.Converter}
 * the consuming app registers as a bean is silently never consulted, since this class is
 * constructed directly by entity authors, outside Spring's bean lifecycle, before {@code
 * RestlessResourceHandler.init()} runs. The three-arg constructor fixes that - pass the app's
 * real, DI-scoped {@code ConversionService} (available wherever this is constructed, same as any
 * other bean dependency) to actually honor custom converters during bulk delete.
 */
public class DefaultDeleteDataSource<E, K>
        extends DeleteDataSource<E, K, DefaultDeleteModel> implements TypedDataSource<DefaultDeleteModel> {

    private final Class<K> idType;
    private final ConversionService conversionService;

    public DefaultDeleteDataSource(SpecificationRepository<E, K> repository, Class<K> idType) {
        this(repository, idType, DefaultConversionService.getSharedInstance());
    }

    public DefaultDeleteDataSource(SpecificationRepository<E, K> repository, Class<K> idType, ConversionService conversionService) {
        super(repository);
        this.idType = idType;
        this.conversionService = conversionService;
    }

    @Override
    public void deleteById(K id) {
        specificationRepository.deleteById(id);
    }

    @Override
    public void deleteAll(DefaultDeleteModel d) {
        // findById+delete (not a bulk JPQL delete) to keep the persistence context's
        // first-level cache consistent with the database - see GadgetDeleteDataSource
        // for the same reasoning.
        d.getIds().forEach(rawId -> {
            K id = conversionService.convert(rawId, idType);
            specificationRepository.findById(id).ifPresent(specificationRepository::delete);
        });
    }

    @Override
    public Class<DefaultDeleteModel> getDtoType() {
        return DefaultDeleteModel.class;
    }
}
