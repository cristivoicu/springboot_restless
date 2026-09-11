package ro.cristivoicu.springbootrestless.controller.update;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class UpdateDataSource<E,K, U extends UpdateModel> extends DataSource<E,K> {
    protected UpdateDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes this by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach it.
    public abstract E update(K id, U updateDto) throws Exception;
}
