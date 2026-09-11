package ro.cristivoicu.springbootrestless.controller.delete;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.DeleteModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class DeleteDataSource<E, K, D extends DeleteModel> extends DataSource<E, K> {
    protected DeleteDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    abstract void deleteEntity(E entity);
    abstract void deleteById(K id);

    abstract void deleteAll(D d);
}
