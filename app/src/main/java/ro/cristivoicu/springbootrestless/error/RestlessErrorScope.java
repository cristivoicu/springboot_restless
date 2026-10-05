package ro.cristivoicu.springbootrestless.error;

/**
 * Marker, implemented by {@code RestlessResourceHandler} and the four hand-subclassed {@code
 * *Controller} base classes ({@code CreateController}/{@code ReadController}/{@code
 * UpdateController}/{@code DeleteController}) - {@code RestlessExceptionHandler}'s {@code
 * @RestControllerAdvice(assignableTypes = RestlessErrorScope.class)} uses this to limit its own
 * exception handling to beans this framework actually registered, rather than every {@code
 * @RestController} in the consumer's application context.
 * <p>
 * Without this, an unscoped {@code @RestControllerAdvice}'s {@code @ExceptionHandler(Exception.class)}
 * catch-all changes error handling for the consumer's <em>own</em> hand-written controllers too -
 * e.g. turning a Spring Security {@code AuthorizationDeniedException} thrown out of a
 * {@code @PreAuthorize}-guarded method into an opaque {@code 500} instead of whatever the
 * consumer's own security configuration (or Spring's own default handling) would have produced.
 * No methods: implementing this is purely a type-level signal, nothing to override.
 */
public interface RestlessErrorScope {
}
