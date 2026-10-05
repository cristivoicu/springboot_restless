package ro.cristivoicu.springbootrestless.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One consistent {@link ProblemDetail} body (RFC 9457, {@code application/problem+json}) for
 * every route this framework registers - generated, hand-wired, or the hand-subclassed {@code
 * *Controller} tier.
 * <p>
 * {@code assignableTypes = RestlessErrorScope.class}: unlike an unscoped {@code
 * @RestControllerAdvice}, this only ever handles an exception thrown out of a bean that actually
 * implements {@link RestlessErrorScope} ({@code RestlessResourceHandler}, or one of the four
 * hand-subclassed {@code *Controller} base classes) - a consumer's own {@code @RestController}
 * (one that, say, throws a Spring Security {@code AuthorizationDeniedException} out of a
 * {@code @PreAuthorize}-guarded method) is untouched by this advice at all, falling through to
 * Spring's own default handling or the consumer's own advice instead of silently becoming a
 * generic {@code 500} here. See {@link RestlessErrorScope}'s own javadoc.
 * <p>
 * RFC 9457's standard members ({@code type}/{@code title}/{@code status}/{@code detail}/
 * {@code instance}) - {@code type} a stable, per-error-kind URI (see {@link #problemType}), not
 * the {@code ProblemDetail} default of {@code about:blank} - plus two extension members every
 * body always carries: {@code timestamp} ({@link Instant#now()}) and {@code errors} (field-level
 * bean-validation violations, each {@code {field, message, code}} - present only for a
 * validation failure; every other response omits the key entirely rather than sending an empty
 * array, since {@code ProblemDetail#setProperty} has no "always include, even when null" mode).
 * {@code instance} is set to the request path.
 * <p>
 * Covers every exception type this framework's own request handling can actually throw:
 * {@link ResponseStatusException} ({@code RestlessResourceHandler}'s manual translations - a
 * malformed id, malformed JSON, a masking guard denial, ...), {@link
 * MethodArgumentNotValidException} (bean validation, thrown by hand for dynamic routes since
 * there's no {@code @Valid @RequestBody} parameter to hang the normal mechanism off of - see
 * {@code RestlessResourceHandler#validate}), {@link HttpMessageNotReadableException}/{@link
 * MethodArgumentTypeMismatchException} (the exception types the hand-written tier gets for the
 * same two failures via the normal argument-resolution pipeline dynamic routes bypass), {@link
 * OptimisticLockingFailureException} (a real concurrent write losing a {@code @Version} race),
 * and {@link DataIntegrityViolationException} (a constraint violation surfacing at the database
 * layer - mapped to {@code 409} with a generic detail, deliberately never the underlying SQL
 * exception's own message, which can embed table/column names or even literal values).
 * <p>
 * {@code @Order(LOWEST_PRECEDENCE)}: a consumer's own {@code @ControllerAdvice} (or a
 * controller-local {@code @ExceptionHandler}) for the same exception type wins over this one -
 * this is a default, not a mandate, same spirit as every other default in this framework.
 * <p>
 * Registered via {@code RestlessAutoConfiguration} (an {@code @Bean}, not component-scanned) -
 * see its javadoc.
 */
@RestControllerAdvice(assignableTypes = RestlessErrorScope.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class RestlessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestlessExceptionHandler.class);

    /**
     * Base for every {@code type} URI this handler sets - doesn't have to resolve to real
     * content (RFC 9457 only asks that it uniquely identify the problem kind), but points
     * somewhere real and stable rather than an opaque {@code urn:}, so a caller who does follow
     * it lands on this project.
     */
    private static final URI PROBLEM_BASE = URI.create("https://github.com/cristivoicu/spring-boot-restless/problems/");

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason() != null ? ex.getReason() : reasonPhraseOf(ex.getStatusCode());
        return respond(ex.getStatusCode(), problemType(slugOf(ex.getStatusCode())), message, request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Sorted: Jakarta Bean Validation makes no ordering guarantee for which constraint
        // violation surfaces first, and RestlessResourceHandler's manual Validator.validate()
        // call (dynamic routes) doesn't necessarily walk fields in the same order a real
        // @Valid @RequestBody resolution does (hand-written routes) - without sorting, the two
        // could report the same violations in a different order, which would have quietly broken
        // ErrorResponseParityTest's byte-for-byte comparison the moment field-level detail became
        // part of the body at all.
        List<RestlessFieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(RestlessExceptionHandler::fieldErrorOf)
                .sorted(Comparator.comparing(RestlessFieldError::field).thenComparing(RestlessFieldError::message))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, problemType("validation-failed"), "Validation failed", request, details);
    }

    private static RestlessFieldError fieldErrorOf(FieldError fieldError) {
        return new RestlessFieldError(fieldError.getField(), fieldError.getDefaultMessage(), fieldError.getCode());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, problemType("malformed-request-body"), "Malformed request body", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Failed to convert '" + ex.getName() + "' to the expected type";
        return respond(HttpStatus.BAD_REQUEST, problemType("type-mismatch"), message, request, List.of());
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
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, problemType("optimistic-lock-conflict"),
                "The resource was modified concurrently - reload and try again", request, List.of());
    }

    /**
     * A constraint violation (unique index, foreign key, not-null column, ...) surfacing at the
     * database layer rather than caught earlier by bean validation - e.g. two concurrent requests
     * racing to create the same natural key. {@code 409}, same status as a losing optimistic-lock
     * race (both are "the database rejected this write because of other data"), with a generic
     * detail: {@code ex.getMessage()}/{@code getMostSpecificCause()} routinely embeds the
     * underlying SQL, table/column/constraint names, or even the offending literal value, none of
     * which belongs in a response body.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, problemType("data-integrity-violation"),
                "The request conflicts with existing data", request, List.of());
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
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse selfDescribing) {
            String message = selfDescribing.getBody().getDetail();
            return respond(selfDescribing.getStatusCode(), problemType(slugOf(selfDescribing.getStatusCode())),
                    message != null ? message : reasonPhraseOf(selfDescribing.getStatusCode()), request, List.of());
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, problemType("internal-server-error"),
                "Internal server error", request, List.of());
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatusCode status, URI type, String message, HttpServletRequest request,
                                                    List<RestlessFieldError> details) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message);
        body.setType(type);
        body.setTitle(reasonPhraseOf(status));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("timestamp", Instant.now());
        if (!details.isEmpty()) {
            body.setProperty("errors", details);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static URI problemType(String slug) {
        return PROBLEM_BASE.resolve(slug);
    }

    /** {@code "Not Found"} -&gt; {@code "not-found"} - a stable, readable slug for {@link #problemType} off any status whose reason phrase this JDK resolves. */
    private static String slugOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String basis = resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
        return basis.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static String reasonPhraseOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }
}
