package ro.cristivoicu.springbootrestless.fixtures.racer;

import jakarta.servlet.http.HttpServletRequest;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deliberately injects a pause between "the row has been loaded" and "the write is about to
 * happen" - exactly the window {@code RestlessResourceHandler#update} runs {@code canAccess} in,
 * now inside the same transaction as the load and the write (Ground rules item 1). {@code
 * ConcurrentUpdateRaceTest} uses this to force a second, fully-independent request to land in
 * between deterministically, rather than hoping a race shows up under raw thread scheduling.
 * Static latches (set/cleared by the test around each use) since {@code
 * RestlessRegistrar}-managed beans are singletons - a per-test instance isn't an option without a
 * dedicated test configuration class. {@code armed}: only the <em>first</em> {@code canAccess}
 * call pauses - the test's own "fast", fully-independent concurrent request hits this exact same
 * guard too, and would otherwise also block on {@code release}, deadlocking against the main
 * thread that's supposed to be the one releasing it.
 */
public class RacerAuthorizationGuard implements AuthorizationGuard<Racer> {

    static volatile CountDownLatch paused;
    static volatile CountDownLatch release;
    static final AtomicBoolean armed = new AtomicBoolean(false);

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, Racer entity) {
        CountDownLatch pausedLatch = paused;
        CountDownLatch releaseLatch = release;
        if (pausedLatch != null && releaseLatch != null && armed.compareAndSet(true, false)) {
            pausedLatch.countDown();
            try {
                if (!releaseLatch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release latch timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return true;
    }
}
