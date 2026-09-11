package ro.cristivoicu.springbootrestless.controller.delete;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.DeleteModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class DeleteDataSource<E, K, D extends DeleteModel> extends DataSource<E, K> {
    protected DeleteDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes these by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach them.
    public abstract void deleteById(K id);

    public abstract void deleteAll(D d);
}
