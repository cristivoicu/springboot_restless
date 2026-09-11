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

    abstract Page<?> findAll(Specification<E> specification, Pageable pageable);
    abstract List<?> findAll(Specification<E> specification);
    abstract E findOne(Specification<E> specification, K id);
    abstract E findOne( K id);
}
