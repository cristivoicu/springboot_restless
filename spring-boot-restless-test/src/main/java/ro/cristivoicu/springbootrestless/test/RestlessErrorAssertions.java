package ro.cristivoicu.springbootrestless.test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts a response body matches {@code RestlessExceptionHandler}'s standard {@code
 * ErrorResponse} shape: {@code {timestamp, status, error, message, path, details}}, with {@code
 * details} always an array (empty, never absent or {@code null}) unless it's a bean-validation
 * failure. Compose with {@code mockMvc.perform(...).andExpect(...)} like any other {@link
 * ResultMatcher} - the same {@code jsonPath}-chained style this framework's own test suite
 * already uses throughout (see e.g. {@code GadgetCrudTest}), just packaged for reuse instead of
 * hand-repeated per consumer.
 */
public final class RestlessErrorAssertions {

    private RestlessErrorAssertions() {
    }

    /** Asserts the response status and that every {@code ErrorResponse} field is present with the right shape, without pinning the exact {@code message}. */
    public static ResultMatcher restlessError(HttpStatus expectedStatus) {
        return matchAll(
                status().is(expectedStatus.value()),
                jsonPath("$.status").value(expectedStatus.value()),
                jsonPath("$.error").value(expectedStatus.getReasonPhrase()),
                jsonPath("$.timestamp").exists(),
                jsonPath("$.message").exists(),
                jsonPath("$.path").exists(),
                jsonPath("$.details").isArray());
    }

    /** Same as {@link #restlessError(HttpStatus)}, plus an exact match on the {@code message} field. */
    public static ResultMatcher restlessError(HttpStatus expectedStatus, String expectedMessage) {
        return matchAll(
                restlessError(expectedStatus),
                jsonPath("$.message").value(expectedMessage));
    }

    /** {@link ResultMatcher} has no built-in varargs composition - this is the whole of it. */
    private static ResultMatcher matchAll(ResultMatcher... matchers) {
        return result -> {
            for (ResultMatcher matcher : matchers) {
                matcher.match(result);
            }
        };
    }
}
