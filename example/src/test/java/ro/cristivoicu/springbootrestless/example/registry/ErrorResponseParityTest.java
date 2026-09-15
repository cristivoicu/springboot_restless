package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 4: proves the dynamic mechanism isn't just "returns 400"/"returns 404" but produces the
 * exact same response as the hand-written reference controller - status, content type, and body,
 * byte for byte. This holds because Spring MVC's exception resolution is global to
 * {@code DispatcherServlet}, not tied to which {@code HandlerMapping} matched the request.
 * <p>
 * {@code @WithMockUser}+{@link CerbosBackedTest}: see {@link Stage1DynamicRegistrationTest}'s
 * javadoc - same reasoning, this stays a pure error-shape-parity test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
class ErrorResponseParityTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void invalidBodyProducesIdenticalResponseOnBothRoutes() throws Exception {
        EmployeeCreateModel invalid = new EmployeeCreateModel();
        invalid.setFirstName("");
        invalid.setLastName("Turing");
        invalid.setEmail("not-an-email");
        String body = objectMapper.writeValueAsString(invalid);

        MvcResult handWritten = mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult dynamic = mockMvc.perform(post("/employees-dynamic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(dynamic.getResponse().getStatus()).isEqualTo(handWritten.getResponse().getStatus());
        assertThat(dynamic.getResponse().getErrorMessage()).isEqualTo(handWritten.getResponse().getErrorMessage());
        // Content, not raw bytes: RestlessExceptionHandler's ErrorResponse carries two fields
        // that are legitimately different between these two calls by design, not by accident -
        // "timestamp" (two separate requests, fired moments apart) and "path" (this comparison
        // is deliberately hitting two different URLs - the whole point of "identical response" is
        // "same status/error/validation content", never "literally the same path").
        assertThat(normalizedBody(dynamic)).isEqualTo(normalizedBody(handWritten));
        assertThat(dynamic.getResponse().getContentType()).isEqualTo(handWritten.getResponse().getContentType());
    }

    private String normalizedBody(MvcResult result) throws Exception {
        var node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        if (node.isObject()) {
            var object = (tools.jackson.databind.node.ObjectNode) node;
            object.remove("timestamp");
            object.remove("path");
        }
        return node.toString();
    }

    @Test
    void missingIdProducesIdenticalResponseOnBothRoutes() throws Exception {
        MvcResult handWritten = mockMvc.perform(get("/employees/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult dynamic = mockMvc.perform(get("/employees-dynamic/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(dynamic.getResponse().getStatus()).isEqualTo(handWritten.getResponse().getStatus());
        assertThat(dynamic.getResponse().getContentAsByteArray()).isEqualTo(handWritten.getResponse().getContentAsByteArray());
    }

    @Test
    void malformedIdReturnsBadRequestOnBothRoutes() throws Exception {
        MvcResult handWritten = mockMvc.perform(get("/employees/{id}", "not-a-number"))
                .andReturn();
        MvcResult dynamic = mockMvc.perform(get("/employees-dynamic/{id}", "not-a-number"))
                .andReturn();

        // extractId()'s manual ConversionService.convert() bypasses the normal @PathVariable
        // resolver (which throws MethodArgumentTypeMismatchException), so it must translate a
        // malformed id to 400 by hand rather than let it surface as an unhandled 500.
        assertThat(dynamic.getResponse().getStatus()).isEqualTo(400);
        assertThat(handWritten.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void malformedJsonBodyReturnsBadRequestOnBothRoutes() throws Exception {
        String truncatedJson = "{\"firstName\": \"Ada\", ";

        MvcResult handWritten = mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(truncatedJson))
                .andReturn();
        MvcResult dynamic = mockMvc.perform(post("/employees-dynamic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(truncatedJson))
                .andReturn();

        // readBody()'s manual ObjectMapper.readValue() bypasses HttpMessageConverter (which
        // throws HttpMessageNotReadableException), so malformed JSON must translate to 400 by
        // hand rather than let it surface as an unhandled 500.
        assertThat(dynamic.getResponse().getStatus()).isEqualTo(400);
        assertThat(handWritten.getResponse().getStatus()).isEqualTo(400);
    }
}
