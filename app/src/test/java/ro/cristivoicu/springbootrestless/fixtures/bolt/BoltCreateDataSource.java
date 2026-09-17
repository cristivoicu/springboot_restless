package ro.cristivoicu.springbootrestless.fixtures.bolt;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

/**
 * Saves the row, <em>then</em> throws for a specific input - deliberately, to prove {@link
 * ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler#createBulk} genuinely rolls
 * a partial write back rather than just validating up front (see {@code
 * BulkTransactionRollbackTest}). This shape isn't contrived: a real create step that has to check
 * something only possible <em>after</em> a row exists (its generated id, say, passed to an
 * external validator) has exactly this same "already wrote it, then found out it was invalid"
 * risk in a plain per-item loop.
 */
@Component
public class BoltCreateDataSource extends CreateDataSource<Bolt, Long, BoltCreateModel> {

    static final String POISON_NAME = "BOOM";

    protected BoltCreateDataSource(BoltRepository repository) {
        super(repository);
    }

    @Override
    public Bolt create(BoltCreateModel createDto) {
        Bolt bolt = new Bolt();
        bolt.setName(createDto.getName());
        Bolt saved = specificationRepository.save(bolt);
        if (POISON_NAME.equals(createDto.getName())) {
            throw new IllegalStateException("Simulated failure discovered only after saving " + saved.getId());
        }
        return saved;
    }
}
