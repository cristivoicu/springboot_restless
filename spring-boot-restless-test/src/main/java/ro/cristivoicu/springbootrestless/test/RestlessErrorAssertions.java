package ro.cristivoicu.springbootrestless.test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts a response body matches {@code RestlessExceptionHandler}'s standard RFC 9457 {@link
 * org.springframework.http.ProblemDetail} shape ({@code application/problem+json}): {@code
 * {type, title, status, detail, instance, timestamp}}, plus {@code errors} (an array of
 * field-level validation messages) on a validation failure only - every other response omits
 * that key entirely rather than sending an empty array. Compose with {@code
 * mockMvc.perform(...).andExpect(...)} like any other {@link ResultMatcher} - the same {@code
 * jsonPath}-chained style this framework's own test suite already uses throughout, just packaged
 * for reuse instead of hand-repeated per consumer.
 */
public final class RestlessErrorAssertions {

    private RestlessErrorAssertions() {
    }

    /**
     * Asserts the response status, content type ({@code application/problem+json}), and that
     * every {@code ProblemDetail} field is present with the right shape, without pinning the
     * exact {@code detail} message.
     */
    public static ResultMatcher restlessError(HttpStatus expectedStatus) {
        return matchAll(
                status().is(expectedStatus.value()),
                content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.status").value(expectedStatus.value()),
                jsonPath("$.title").value(expectedStatus.getReasonPhrase()),
                jsonPath("$.timestamp").exists(),
                jsonPath("$.detail").exists(),
                jsonPath("$.instance").exists());
    }

    /** Same as {@link #restlessError(HttpStatus)}, plus an exact match on the {@code detail} field. */
    public static ResultMatcher restlessError(HttpStatus expectedStatus, String expectedMessage) {
        return matchAll(
                restlessError(expectedStatus),
                jsonPath("$.detail").value(expectedMessage));
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
