package ro.cristivoicu.springbootrestless.datasource;

import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class DataSource<E,K> {
    protected final SpecificationRepository<E, K> specificationRepository;

    protected DataSource(SpecificationRepository<E, K> specificationRepository) {
        this.specificationRepository = specificationRepository;
    }
}
