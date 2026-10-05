package ro.cristivoicu.springbootrestless.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ground rules item 7: a database-level constraint violation must map to {@code 409} with a
 * generic detail - never the underlying SQL exception's own message, which routinely embeds
 * table/column/constraint names or literal values that don't belong in a response body.
 */
class RestlessExceptionHandlerDataIntegrityTest {

    @Test
    void mapsDataIntegrityViolationTo409WithoutLeakingTheUnderlyingSql() {
        RestlessExceptionHandler handler = new RestlessExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nuggets");
        String sqlLeak = "Unique index or primary key violation: \"PUBLIC.NUGGET(CODE) VALUES ('a-code')\"";

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(sqlLeak), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).doesNotContain("PUBLIC.NUGGET").doesNotContain("a-code");
    }
}
