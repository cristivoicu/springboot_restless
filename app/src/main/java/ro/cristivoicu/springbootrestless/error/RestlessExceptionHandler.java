package ro.cristivoicu.springbootrestless.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * One consistent {@link ErrorResponse} body for every route this framework registers - generated,
 * hand-wired, or the original hand-written {@code @RestController}s in the same app, since {@code
 * @RestControllerAdvice}'s exception resolution is global to {@code DispatcherServlet}, not tied
 * to which {@code HandlerMapping} matched the request (the same fact {@code
 * ErrorResponseParityTest} already relies on to prove generated and hand-written routes fail
 * identically). Before this class existed, that "identically" meant "identically whatever Spring
 * Boot's own default error handling happened to produce" - undocumented, unversioned, and
 * different in shape depending on which exception type happened to fire underneath. This is that
 * shape, made explicit.
 * <p>
 * Covers every exception type this framework's own request handling can actually throw:
 * {@link ResponseStatusException} ({@code RestlessResourceHandler}'s manual translations - a
 * malformed id, malformed JSON, a masking guard denial, ...), {@link
 * MethodArgumentNotValidException} (bean validation, thrown by hand for dynamic routes since
 * there's no {@code @Valid @RequestBody} parameter to hang the normal mechanism off of - see
 * {@code RestlessResourceHandler#validate}), plus {@link HttpMessageNotReadableException} and
 * {@link MethodArgumentTypeMismatchException} - the exception types the *hand-written* {@code
 * @RestController}s in this same app get for the same two failures, via the normal argument-
 * resolution pipeline dynamic routes bypass. Handling all four (not just the two dynamic routes
 * throw) is what keeps the two paths byte-for-byte identical.
 * <p>
 * {@code @Order(LOWEST_PRECEDENCE)}: a consumer's own {@code @ControllerAdvice} (or a
 * controller-local {@code @ExceptionHandler}) for the same exception type wins over this one -
 * this is a default, not a mandate, same spirit as every other default in this framework.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RestlessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestlessExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason() != null ? ex.getReason() : reasonPhraseOf(ex.getStatusCode());
        return respond(ex.getStatusCode(), message, request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Sorted: Jakarta Bean Validation makes no ordering guarantee for which constraint
        // violation surfaces first, and RestlessResourceHandler's manual Validator.validate()
        // call (dynamic routes) doesn't necessarily walk fields in the same order a real
        // @Valid @RequestBody resolution does (hand-written routes) - without sorting, the two
        // could report the same violations in a different order, which would have quietly broken
        // ErrorResponseParityTest's byte-for-byte comparison the moment field-level detail became
        // part of the body at all.
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted(Comparator.naturalOrder())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Failed to convert '" + ex.getName() + "' to the expected type";
        return respond(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    /**
     * A JPA {@code @Version}-annotated entity racing a concurrent write: Hibernate/Spring Data
     * JPA already throw this on a stale {@code save()} with zero framework code needed once an
     * entity author adds the annotation - this handler is the only piece that was missing (see
     * {@code RestlessResourceHandler}'s own {@code readVersion}/{@code checkIfMatch}, the
     * <em>opt-in, precondition-based</em> counterpart to this <em>always-on, write-time</em> one -
     * this fires regardless of whether the client ever sent an {@code If-Match} header at all).
     * 409, not 412: unlike a failed {@code If-Match} precondition (checked before any write is
     * attempted), this reports a write that was actually attempted and rejected by the database.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "The resource was modified concurrently - reload and try again", request, List.of());
    }

    /**
     * Last resort for anything not one of the more specific types already handled above.
     * {@code @ExceptionHandler}'s {@code value()} is bounded to {@code Class<? extends
     * Throwable>}, so - unlike the handlers above - Spring's own dispatch can't key a method
     * directly on {@code org.springframework.web.ErrorResponse} (a plain interface, not a {@code
     * Throwable}); checking {@code instanceof} by hand inside one {@code Exception}-typed handler
     * is what that constraint leaves available. It matters in practice for {@code
     * NoHandlerFoundException} (a request to a path nothing registered at all): unlike a truly
     * unanticipated failure, it already carries the right status (404) and a {@code
     * ProblemDetail} body, so deriving from it directly - rather than defaulting to 500 - is both
     * correct and simpler than hand-listing every {@code ErrorResponse}-implementing exception
     * Spring might throw. Only a *genuinely* unanticipated exception (the {@code else} branch)
     * gets logged at ERROR - every handler above, and the {@code ErrorResponse} branch here, are
     * expected, routine outcomes that don't warrant one.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse selfDescribing) {
            String message = selfDescribing.getBody().getDetail();
            return respond(selfDescribing.getStatusCode(),
                    message != null ? message : reasonPhraseOf(selfDescribing.getStatusCode()), request, List.of());
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatusCode status, String message, HttpServletRequest request,
                                                    List<String> details) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), reasonPhraseOf(status), message,
                request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }

    private static String reasonPhraseOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }
}
