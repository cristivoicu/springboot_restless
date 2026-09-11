package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the dynamic mechanism isn't just "returns 400"/"returns 404" but produces the exact
 * same response as the hand-written reference controller - status, content type, and body,
 * byte for byte. This holds because Spring MVC's exception resolution is global to
 * {@code DispatcherServlet}, not tied to which {@code HandlerMapping} matched the request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorResponseParityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void invalidBodyProducesIdenticalResponseOnBothRoutes() throws Exception {
        GadgetCreateModel invalid = new GadgetCreateModel();
        invalid.setFirstName("");
        invalid.setLastName("Turing");
        invalid.setEmail("not-an-email");
        String body = objectMapper.writeValueAsString(invalid);

        MvcResult handWritten = mockMvc.perform(post("/gadgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult dynamic = mockMvc.perform(post("/gadgets-dynamic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(dynamic.getResponse().getStatus()).isEqualTo(handWritten.getResponse().getStatus());
        assertThat(dynamic.getResponse().getErrorMessage()).isEqualTo(handWritten.getResponse().getErrorMessage());
        assertThat(dynamic.getResponse().getContentAsByteArray()).isEqualTo(handWritten.getResponse().getContentAsByteArray());
        assertThat(dynamic.getResponse().getContentType()).isEqualTo(handWritten.getResponse().getContentType());
    }

    @Test
    void missingIdProducesIdenticalResponseOnBothRoutes() throws Exception {
        MvcResult handWritten = mockMvc.perform(get("/gadgets/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andReturn();

        MvcResult dynamic = mockMvc.perform(get("/gadgets-dynamic/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(dynamic.getResponse().getStatus()).isEqualTo(handWritten.getResponse().getStatus());
        assertThat(dynamic.getResponse().getContentAsByteArray()).isEqualTo(handWritten.getResponse().getContentAsByteArray());
    }

    @Test
    void malformedIdReturnsBadRequestOnBothRoutes() throws Exception {
        MvcResult handWritten = mockMvc.perform(get("/gadgets/{id}", "not-a-number"))
                .andReturn();
        MvcResult dynamic = mockMvc.perform(get("/gadgets-dynamic/{id}", "not-a-number"))
                .andReturn();

        assertThat(dynamic.getResponse().getStatus()).isEqualTo(400);
        assertThat(handWritten.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void malformedJsonBodyReturnsBadRequestOnBothRoutes() throws Exception {
        String truncatedJson = "{\"firstName\": \"Ada\", ";

        MvcResult handWritten = mockMvc.perform(post("/gadgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(truncatedJson))
                .andReturn();
        MvcResult dynamic = mockMvc.perform(post("/gadgets-dynamic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(truncatedJson))
                .andReturn();

        assertThat(dynamic.getResponse().getStatus()).isEqualTo(400);
        assertThat(handWritten.getResponse().getStatus()).isEqualTo(400);
    }
}
