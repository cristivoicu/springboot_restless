package ro.cristivoicu.springbootrestless.controller.read;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.List;

public abstract class ReadDataSource<E,K,R extends SearchDto> extends DataSource<E,K> {
    protected ReadDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes these by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach them.
    public abstract Page<E> findAll(Specification<E> specification, Pageable pageable);
    public abstract List<E> findAll(Specification<E> specification);
    public abstract E findOne(Specification<E> specification, K id);
    public abstract E findOne(K id);
}
