package ro.cristivoicu.springbootrestless.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 7: {@code RestlessExceptionHandler} must be scoped to {@code
 * RestlessErrorScope} (restless handlers + the hand-subclassed {@code *Controller} tier), not a
 * global {@code @RestControllerAdvice} that changes error handling for a consumer's own,
 * unrelated controllers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestlessExceptionHandlerScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unrelatedControllerExceptionIsNotHandledByRestlessExceptionHandler() {
        // No @ExceptionHandler anywhere matches a plain IllegalStateException thrown from a
        // controller RestlessExceptionHandler's assignableTypes scoping excludes - MockMvc
        // propagates it as-is (wrapped in a ServletException) rather than rendering our
        // application/problem+json ProblemDetail shape for it. That propagation IS the proof:
        // before the fix, the unscoped advice's Exception catch-all would have handled this and
        // returned a normal 500 response here instead.
        assertThatThrownBy(() -> mockMvc.perform(get("/unrelated/boom")))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("not a restless failure");
    }

    @Test
    void restlessRouteStillGetsTheProblemJsonShape() throws Exception {
        mockMvc.perform(get("/gizmos/{id}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
