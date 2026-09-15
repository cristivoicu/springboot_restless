package ro.cristivoicu.springbootrestless.controller.patch;

import ro.cristivoicu.springbootrestless.datasource.DataSource;
import ro.cristivoicu.springbootrestless.models.PatchModel;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

/**
 * Partial update: unlike {@link ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource}
 * (a full replace - every field expected, validated as such), a patch only ever touches the
 * fields the client actually sent. {@code null} on a {@code PatchModel} field means "the client
 * didn't send this field, leave it alone" - not "clear it"; a client wanting to explicitly null
 * out a field still needs a full {@code PUT} update for that. This is the same shallow-merge
 * tradeoff most frameworks that layer {@code PATCH} onto a reflective default make, in exchange
 * for not having to distinguish "absent" from "explicitly null" once a JSON body has already been
 * deserialized into a plain POJO.
 * <p>
 * Entirely opt-in, unlike Create/Read/Update/Delete: {@code RestlessResourceHandler} doesn't
 * require one at all (see {@code getPatchDataSource()}'s default) - a resource that never
 * overrides it simply has no {@code PATCH} route registered.
 */
public abstract class PatchDataSource<E, K, P extends PatchModel> extends DataSource<E, K> {
    protected PatchDataSource(SpecificationRepository<E, K> specificationRepository) {
        super(specificationRepository);
    }

    // public, not protected: RestlessResourceHandler (resource package) invokes this by
    // composition, not inheritance, so protected (same-package-or-subtype) access won't reach it.
    public abstract E patch(K id, P patchDto) throws Exception;
}
