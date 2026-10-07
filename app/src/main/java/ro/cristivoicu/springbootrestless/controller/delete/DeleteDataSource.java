package ro.cristivoicu.springbootrestless.controller.delete;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.DeleteModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

public abstract class DeleteDataSource<E, K, D extends DeleteModel> extends DataSource<E, K> {
    protected DeleteDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes these by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach them.
    public abstract void deleteById(K id);

    public abstract void deleteAll(D d);

    /**
     * Context-aware overload - the handler always calls this one, never {@link #deleteById(Object)}
     * directly (see {@code CreateDataSource#create(Object, RestlessContext)}'s own javadoc for
     * why that keeps every existing override unchanged).
     */
    public void deleteById(K id, RestlessContext context) {
        deleteById(id);
    }

    /** Context-aware overload - the handler always calls this one. Defaults to {@link #deleteAll(DeleteModel)}. */
    public void deleteAll(D d, RestlessContext context) {
        deleteAll(d);
    }
}
