package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.datasource.defaults.DefaultPatchDataSource;

/**
 * Escape-hatch proof, same reasoning as {@link DoohickeyAuthorizationGuard}: referenced via
 * {@code @RestlessEntity(patchDataSource = DoohickeyPatchDataSource.class)} on {@link Doohickey},
 * so the generated resource gets a {@code PATCH} route at all. A small named subclass fixing
 * {@link DefaultPatchDataSource}'s type parameters for {@link Doohickey} specifically - {@code
 * patchDataSource}'s {@code Class<?>} attribute can't name the generic class directly (see its
 * javadoc). No logic of its own: the reflective default (skip {@code null} fields, copy the rest)
 * is exactly what {@link Doohickey} needs.
 */
@Component
public class DoohickeyPatchDataSource extends DefaultPatchDataSource<Doohickey, Long, DoohickeyPatchModel> {

    public DoohickeyPatchDataSource(DoohickeyRepository repository) {
        super(repository, DoohickeyPatchModel.class);
    }
}
