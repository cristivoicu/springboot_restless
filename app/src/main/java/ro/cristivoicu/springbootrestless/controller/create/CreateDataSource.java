package ro.cristivoicu.springbootrestless.controller.create;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class CreateDataSource<E, K, C extends CreateModel> extends DataSource<E, K> {
    protected CreateDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes this by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach it.
    public abstract E create(C entity) throws Exception;
}
