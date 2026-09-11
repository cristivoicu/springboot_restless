package ro.cristivoicu.springbootrestless.datasource.defaults;

import org.springframework.core.convert.support.DefaultConversionService;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.datasource.TypedDataSource;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Default {@link DeleteDataSource}: deletes by id, and bulk-deletes via {@link DefaultDeleteModel}
 * (fixed — the delete DTO shape is always just "a list of ids", so there's no reason for most
 * entities to declare their own). Ids are converted from string via a standalone
 * {@link DefaultConversionService}, independent of the resource's own DI-scoped
 * {@code ConversionService} (this class is constructed directly by entity authors, outside
 * Spring's bean lifecycle, before {@code RestlessResourceHandler.init()} runs).
 */
public class DefaultDeleteDataSource<E, K>
        extends DeleteDataSource<E, K, DefaultDeleteModel> implements TypedDataSource<DefaultDeleteModel> {

    private final Class<K> idType;

    public DefaultDeleteDataSource(SpecificationRepository<E, K> repository, Class<K> idType) {
        super(repository);
        this.idType = idType;
    }

    @Override
    public void deleteById(K id) {
        specificationRepository.deleteById(id);
    }

    @Override
    public void deleteAll(DefaultDeleteModel d) {
        // findById+delete (not a bulk JPQL delete) to keep the persistence context's
        // first-level cache consistent with the database - see EmployeeDeleteDataSource
        // for the same reasoning.
        d.getIds().forEach(rawId -> {
            K id = DefaultConversionService.getSharedInstance().convert(rawId, idType);
            specificationRepository.findById(id).ifPresent(specificationRepository::delete);
        });
    }

    @Override
    public Class<DefaultDeleteModel> getDtoType() {
        return DefaultDeleteModel.class;
    }
}
