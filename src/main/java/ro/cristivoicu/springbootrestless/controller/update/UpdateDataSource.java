package ro.cristivoicu.springbootrestless.controller.update;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class UpdateDataSource<E,K, D extends UpdateModel> extends DataSource<E,K> {
    protected UpdateDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    abstract void deleteById(K id);
    abstract void delete(D d);

}
