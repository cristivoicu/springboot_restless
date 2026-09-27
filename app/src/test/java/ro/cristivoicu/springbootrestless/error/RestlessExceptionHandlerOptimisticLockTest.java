package ro.cristivoicu.springbootrestless.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context needed - {@code RestlessExceptionHandler} is a POJO) for the
 * write-time half of optimistic concurrency: a real stale {@code @Version} write, wherever it's
 * thrown from, must map to 409 with the standard RFC 9457 {@link ProblemDetail} shape. The
 * precondition half ({@code If-Match} → 412, checked before any write) is proved separately in
 * {@code WidgetLifecycleTest}, since that one needs no race at all - just a request header.
 */
class RestlessExceptionHandlerOptimisticLockTest {

    @Test
    void mapsOptimisticLockingFailureTo409WithNoDetails() {
        RestlessExceptionHandler handler = new RestlessExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/widgets/1");

        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("stale write"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getTitle()).isEqualTo("Conflict");
        assertThat(body.getInstance()).isEqualTo(java.net.URI.create("/widgets/1"));
        assertThat(body.getProperties()).doesNotContainKey("errors");
    }
}
