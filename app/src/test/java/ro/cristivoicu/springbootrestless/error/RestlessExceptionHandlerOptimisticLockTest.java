package ro.cristivoicu.springbootrestless.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context needed - {@code RestlessExceptionHandler} is a POJO) for the
 * write-time half of optimistic concurrency: a real stale {@code @Version} write, wherever it's
 * thrown from, must map to 409 with the standard {@link ErrorResponse} shape. The precondition
 * half ({@code If-Match} → 412, checked before any write) is proved separately in {@code
 * WidgetLifecycleTest}, since that one needs no race at all - just a request header.
 */
class RestlessExceptionHandlerOptimisticLockTest {

    @Test
    void mapsOptimisticLockingFailureTo409WithNoDetails() {
        RestlessExceptionHandler handler = new RestlessExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/widgets/1");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("stale write"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.error()).isEqualTo("Conflict");
        assertThat(body.path()).isEqualTo("/widgets/1");
        assertThat(body.details()).isEmpty();
    }
}
