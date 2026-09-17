package ro.cristivoicu.springbootrestless.test;

import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts a response body matches {@code RestlessResourceHandler}'s standard {@code
 * PageableResponse} envelope: {@code {totalPages, totalElements, pageSize, body}}. Note there is
 * no current-page-index field on the wire today - only totals, size, and content - so there's
 * nothing here to assert about "which page" beyond what the caller already knows from its own
 * request.
 */
public final class RestlessPageAssertions {

    private RestlessPageAssertions() {
    }

    /** Shape-only: every envelope field is present and {@code body} is an array - no assertion on actual counts or content. */
    public static ResultMatcher restlessPage() {
        return matchAll(
                status().isOk(),
                jsonPath("$.totalPages").exists(),
                jsonPath("$.totalElements").exists(),
                jsonPath("$.pageSize").exists(),
                jsonPath("$.body").isArray());
    }

    /**
     * {@link #restlessPage()} plus exact {@code totalElements}/{@code pageSize} values - matched
     * via the explicit-target-type {@code value(Matcher, Class)} overload so a {@code long}/{@code
     * int} literal here compares correctly regardless of whether the JSON provider under the hood
     * parses a small number as a JSON {@code Integer} or {@code Long}.
     */
    public static ResultMatcher restlessPage(long expectedTotalElements, int expectedPageSize) {
        return matchAll(
                restlessPage(),
                jsonPath("$.totalElements").value(equalTo(expectedTotalElements), Long.class),
                jsonPath("$.pageSize").value(equalTo(expectedPageSize), Integer.class));
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
