package ro.cristivoicu.springbootrestless.resource;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Callable;

/**
 * Extracted out of {@code RestlessResourceHandler} (Ground rules Phase 2 item 14 - "no
 * public-surface change": both methods here were already {@code private} on that class, so
 * moving them changes nothing any subclass/consumer could see). Not {@code @Transactional}:
 * every handler method calling into this is deliberately {@code final} (one shared {@link
 * java.lang.reflect.Method} object dispatched reflectively across every resource instance - see
 * {@code RestlessResourceHandler}'s own class javadoc), and Spring's proxy-based {@code
 * @Transactional} support cannot intercept a final method at all - CGLIB can't override it, so
 * the annotation would silently do nothing while still triggering a (useless) proxy and its own
 * startup warnings for every other final method there.
 */
final class TransactionSupport {

    private final PlatformTransactionManager transactionManager;

    TransactionSupport(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * {@code null} {@code transactionManager} (no {@code PlatformTransactionManager} configured)
     * runs {@code work} with no transaction boundary at all - today's original behavior, not a
     * broken one: every {@code Default*DataSource}/hand-written {@code *DataSource} still works
     * without it, just without the atomicity guarantee a real write benefits from. Only {@code
     * RestlessRegistrar} constructs this with a real, non-null manager (the {@code
     * JpaTransactionManager} Spring Data JPA already auto-configures) - every test that
     * constructs a {@code RestlessResourceHandler} by hand and never calls {@code init(...)} at
     * all keeps this {@code null} fallback.
     * <p>
     * {@link Callable}, not a plain {@link java.util.function.Supplier}: every write-path caller
     * (create/update/patch/delete, and the data sources they call into) declares {@code throws
     * Exception} - {@code TransactionCallback#doInTransaction} has no {@code throws} clause of
     * its own, so a checked exception thrown inside has to be wrapped to escape the callback,
     * then unwrapped back to its original type once outside the transaction (any {@code
     * RuntimeException} - including the wrapper - already triggers rollback on the way out,
     * which is exactly the point).
     */
    <T> T inTransaction(Callable<T> work) throws Exception {
        if (transactionManager == null) {
            return work.call();
        }
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                try {
                    return work.call();
                } catch (Exception e) {
                    throw new TransactionRollbackWrapper(e);
                }
            });
        } catch (TransactionRollbackWrapper wrapper) {
            throw wrapper.cause;
        }
    }

    /**
     * Read counterpart to {@link #inTransaction} - wraps a single-entity/page fetch plus its
     * {@code Mapper}/{@code RestlessEmbedResolver} call in one {@code readOnly} transaction, so a
     * lazy JPA association a hand-written {@code Mapper} or {@code @RestlessEmbed} field touches
     * is still initializable when the consumer runs with {@code spring.jpa.open-in-view=false}
     * (Spring Boot's own OSIV default is {@code true}, which papers over exactly this - a
     * consumer who turns it off, the generally-recommended production setting, would otherwise
     * hit a {@link org.hibernate.LazyInitializationException} the moment mapping touched an
     * uninitialized proxy outside any session at all). {@code readOnly = true}: this path never
     * writes, so Hibernate can skip dirty-checking - a real (if modest) win, not just a label.
     * Same "no {@link PlatformTransactionManager} configured means no transaction boundary at
     * all" fallback as {@link #inTransaction}.
     */
    <T> T inReadOnlyTransaction(Callable<T> work) throws Exception {
        if (transactionManager == null) {
            return work.call();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        try {
            return template.execute(status -> {
                try {
                    return work.call();
                } catch (Exception e) {
                    throw new TransactionRollbackWrapper(e);
                }
            });
        } catch (TransactionRollbackWrapper wrapper) {
            throw wrapper.cause;
        }
    }

    /** See {@link #inTransaction}'s own javadoc for why this exists at all. */
    private static final class TransactionRollbackWrapper extends RuntimeException {
        private final Exception cause;

        TransactionRollbackWrapper(Exception cause) {
            this.cause = cause;
        }
    }
}
