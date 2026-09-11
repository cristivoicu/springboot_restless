package ro.cristivoicu.springbootrestless.controller.create;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class CreateDataSource<E, K, C extends CreateModel> extends DataSource<E, K> {
    protected CreateDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    abstract E create(C entity) throws Exception;
}
