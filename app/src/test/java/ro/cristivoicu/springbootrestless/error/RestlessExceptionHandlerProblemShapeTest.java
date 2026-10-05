package ro.cristivoicu.springbootrestless.error;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ground rules item 7: every {@code ProblemDetail} now carries a stable, per-error-kind
 * {@code type} URI (previously the RFC 9457 default of {@code about:blank}, which can't
 * distinguish one error kind from another), and a validation failure's {@code errors} are now
 * objects ({@code {field, message, code}}), not a single {@code "field: message"} string a
 * client would have to parse by hand.
 */
class RestlessExceptionHandlerProblemShapeTest {

    private static final MethodParameter DUMMY_PARAMETER;

    static {
        try {
            DUMMY_PARAMETER = new MethodParameter(
                    RestlessExceptionHandlerProblemShapeTest.class.getDeclaredMethod("dummyTarget", Object.class), 0);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private static void dummyTarget(Object body) {
    }

    @Test
    void responseStatusExceptionGetsAStableTypeUriKeyedByStatus() {
        RestlessExceptionHandler handler = new RestlessExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nuggets/missing");

        ResponseEntity<ProblemDetail> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity missing not found"), request);

        URI type = response.getBody().getType();
        assertThat(type).isNotEqualTo(URI.create("about:blank"));
        assertThat(type.toString()).endsWith("not-found");
    }

    @Test
    void differentStatusesGetDifferentTypeUris() {
        RestlessExceptionHandler handler = new RestlessExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");

        URI forbidden = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "nope"), request).getBody().getType();
        URI badRequest = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad"), request).getBody().getType();

        assertThat(forbidden).isNotEqualTo(badRequest);
    }

    @Test
    void validationErrorsAreStructuredObjectsNotAFlatString() {
        RestlessExceptionHandler handler = new RestlessExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nuggets");

        Object target = new Object();
        BindingResult bindingResult = new BeanPropertyBindingResult(target, "NuggetCreateModel");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "NuggetCreateModel", "code", null, false, new String[]{"NotBlank"}, null, "must not be blank"));

        ResponseEntity<ProblemDetail> response = handler.handleValidation(
                new MethodArgumentNotValidException(DUMMY_PARAMETER, bindingResult), request);

        @SuppressWarnings("unchecked")
        List<RestlessFieldError> errors = (List<RestlessFieldError>) response.getBody().getProperties().get("errors");
        assertThat(errors).hasSize(1);
        RestlessFieldError error = errors.get(0);
        assertThat(error.field()).isEqualTo("code");
        assertThat(error.message()).isEqualTo("must not be blank");
        assertThat(error.code()).isEqualTo("NotBlank");
    }
}
