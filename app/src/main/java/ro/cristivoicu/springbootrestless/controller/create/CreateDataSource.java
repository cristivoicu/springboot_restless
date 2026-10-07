package ro.cristivoicu.springbootrestless.controller.create;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

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
     * Context-aware overload - the handler always calls this one, never the bare {@link
     * #create(Object)} directly, so a hand-written {@code CreateDataSource} that only overrides
     * that one still gets called unchanged (this default simply delegates to it, ignoring {@code
     * context}). Override this one instead when entity-specific create logic actually needs the
     * request's path variables/{@code Authentication} (e.g. a nested resource's own parent id -
     * though for that specific case the parent field is already force-written by the handler
     * itself after this returns, see {@code RestlessParent}'s javadoc, so most nested resources
     * never need to override this at all).
     */
    public E create(C entity, RestlessContext context) throws Exception {
        return create(entity);
    }

    /**
     * Bulk create, backing {@code POST {basePath}/bulk} - one {@link #create(Object)} call per
     * item, in order, same as {@code Mapper<E,D>.map(List<E>)}'s own default per-element loop. A
     * hand-written {@code CreateDataSource} with entity-specific logic in {@link #create} gets
     * bulk support for free through this default; override only for a genuinely different bulk
     * strategy (a single batched {@code saveAll}, say).
     */
    public List<E> createAll(List<C> items) throws Exception {
        List<E> created = new ArrayList<>(items.size());
        for (C item : items) {
            created.add(create(item));
        }
        return created;
    }

    /**
     * Context-aware overload - the handler always calls this one. Defaults to looping {@link
     * #create(Object, RestlessContext)} (not {@link #createAll(List)}), so a hand-written {@code
     * CreateDataSource} that only overrides the single-item context-aware {@code create} still
     * gets correct bulk behavior for free, the same "override one, get bulk for free" shape
     * {@link #createAll(List)} itself already has for the non-context {@link #create(Object)}.
     */
    public List<E> createAll(List<C> items, RestlessContext context) throws Exception {
        List<E> created = new ArrayList<>(items.size());
        for (C item : items) {
            created.add(create(item, context));
        }
        return created;
    }
}
