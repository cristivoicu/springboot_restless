package ro.cristivoicu.springbootrestless.controller.read;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.ArrayList;
import java.util.Collection;
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

    /**
     * Bulk fetch by id (Ground rules Phase 2 item 10) - {@code updateBulk}/{@code deleteAll}'s
     * guard-check loops call this once instead of looping {@link #findOne} per id. Concrete, not
     * abstract: this default (one {@link #findOne} call per id) is correct for every existing
     * hand-written {@code ReadDataSource} without requiring a new override, just not the
     * single-query win {@link ro.cristivoicu.springbootrestless.datasource.defaults.DefaultReadDataSource}
     * overrides this with. A missing id is simply absent from the result, same as a single
     * {@link #findOne} returning {@code null} for one.
     */
    public List<E> findAllById(Collection<K> ids) {
        List<E> found = new ArrayList<>(ids.size());
        for (K id : ids) {
            E entity = findOne(id);
            if (entity != null) {
                found.add(entity);
            }
        }
        return found;
    }
}
