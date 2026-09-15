package ro.cristivoicu.springbootrestless.controller.create;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.ArrayList;
import java.util.List;

public abstract class CreateDataSource<E, K, C extends CreateModel> extends DataSource<E, K> {
    protected CreateDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes this by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach it.
    public abstract E create(C entity) throws Exception;

    /**
     * Bulk create, backing {@code POST {basePath}/bulk} - one {@link #create} call per item, in
     * order, same as {@code Mapper<E,D>.map(List<E>)}'s own default per-element loop. A hand-
     * written {@code CreateDataSource} with entity-specific logic in {@link #create} gets bulk
     * support for free through this default; override only for a genuinely different bulk
     * strategy (a single batched {@code saveAll}, say).
     */
    public List<E> createAll(List<C> items) throws Exception {
        List<E> created = new ArrayList<>(items.size());
        for (C item : items) {
            created.add(create(item));
        }
        return created;
    }
}
