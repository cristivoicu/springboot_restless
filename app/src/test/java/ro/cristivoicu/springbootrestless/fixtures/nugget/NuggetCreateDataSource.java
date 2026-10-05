package ro.cristivoicu.springbootrestless.fixtures.nugget;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

/**
 * Hand-written, not {@code DefaultCreateDataSource}: {@code Nugget}'s {@code @Id} is a
 * client-supplied natural key ({@code code}), which {@code ProtectedEntityFields} (Ground rules
 * item 6) now deliberately strips from any reflective copy - exactly right for a surrogate key,
 * wrong here. A natural-key entity needing the default CUD tier is expected to hand-write this
 * one verb, same as any other create logic a plain field copy can't express.
 */
@Component
public class NuggetCreateDataSource extends CreateDataSource<Nugget, String, NuggetCreateModel> {

    public NuggetCreateDataSource(NuggetRepository repository) {
        super(repository);
    }

    @Override
    public Nugget create(NuggetCreateModel createDto) {
        Nugget nugget = new Nugget(createDto.getCode(), createDto.getLabel());
        return specificationRepository.save(nugget);
    }
}
