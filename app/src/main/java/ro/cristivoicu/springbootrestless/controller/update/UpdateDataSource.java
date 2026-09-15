package ro.cristivoicu.springbootrestless.controller.update;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class UpdateDataSource<E,K, U extends UpdateModel> extends DataSource<E,K> {
    protected UpdateDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes this by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach it.
    public abstract E update(K id, U updateDto) throws Exception;

    /**
     * Bulk update, backing {@code PUT {basePath}/bulk} - one {@link #update} call per entry, in
     * whatever order {@code items} iterates, same "loop the single-item verb" default {@link
     * ro.cristivoicu.springbootrestless.controller.create.CreateDataSource#createAll} uses.
     * Override only for a genuinely different bulk strategy.
     */
    public List<E> updateAll(Map<K, U> items) throws Exception {
        List<E> updated = new ArrayList<>(items.size());
        for (Map.Entry<K, U> entry : items.entrySet()) {
            updated.add(update(entry.getKey(), entry.getValue()));
        }
        return updated;
    }
}
