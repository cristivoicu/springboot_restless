package ro.cristivoicu.springbootrestless.controller.update;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

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
     * Context-aware overload - the handler always calls this one, never the bare {@link
     * #update(Object, UpdateModel)} directly (see {@code CreateDataSource#create(Object,
     * RestlessContext)}'s own javadoc for why that keeps every existing override unchanged).
     */
    public E update(K id, U updateDto, RestlessContext context) throws Exception {
        return update(id, updateDto);
    }

    /**
     * Bulk update, backing {@code PUT {basePath}/bulk} - one {@link #update(Object, UpdateModel)}
     * call per entry, in whatever order {@code items} iterates, same "loop the single-item verb"
     * default {@link ro.cristivoicu.springbootrestless.controller.create.CreateDataSource#createAll}
     * uses. Override only for a genuinely different bulk strategy.
     */
    public List<E> updateAll(Map<K, U> items) throws Exception {
        List<E> updated = new ArrayList<>(items.size());
        for (Map.Entry<K, U> entry : items.entrySet()) {
            updated.add(update(entry.getKey(), entry.getValue()));
        }
        return updated;
    }

    /**
     * Context-aware overload - the handler always calls this one. Defaults to looping {@link
     * #update(Object, UpdateModel, RestlessContext)}, same "override the single-item verb, get
     * bulk for free" shape {@link #updateAll(Map)} itself already has.
     */
    public List<E> updateAll(Map<K, U> items, RestlessContext context) throws Exception {
        List<E> updated = new ArrayList<>(items.size());
        for (Map.Entry<K, U> entry : items.entrySet()) {
            updated.add(update(entry.getKey(), entry.getValue(), context));
        }
        return updated;
    }
}
