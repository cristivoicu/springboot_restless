package ro.cristivoicu.springbootrestless.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * The error body every generated route answers with, when the application already has its own
 * error shape. Register one bean and {@code RestlessExceptionHandler} serializes what it returns
 * (as {@code application/json}) instead of its RFC 9457 {@code ProblemDetail}:
 * <pre>{@code
 * @Bean
 * RestlessErrorBodyFactory apiErrors() {
 *     return (status, message, fieldErrors, request) ->
 *             ApiError.of(status.value(), message, request.getRequestURI());
 * }
 * }</pre>
 * Status codes, messages and which exceptions map to which status stay the framework's.
 */
@FunctionalInterface
public interface RestlessErrorBodyFactory {

    Object body(HttpStatusCode status, String message, List<RestlessFieldError> fieldErrors, HttpServletRequest request);
}
