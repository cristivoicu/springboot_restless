package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ro.cristivoicu.springbootrestless.error.RestlessErrorBodyFactory;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With a {@code RestlessErrorBodyFactory} bean, generated routes answer in the application's own error shape. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorBodyFactoryTest.AppErrors.class)
class ErrorBodyFactoryTest extends CerbosBackedTest {

    @TestConfiguration
    static class AppErrors {
        @Bean
        RestlessErrorBodyFactory appErrorBody() {
            return (status, message, fieldErrors, request) -> Map.of(
                    "code", status.value(), "text", message, "path", request.getRequestURI(), "fields", fieldErrors.size());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusErrorsUseTheApplicationsShape() throws Exception {
        mockMvc.perform(put("/notebooks/{id}", Long.MAX_VALUE).with(user("nobody"))
                        .contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.path").value("/notebooks/" + Long.MAX_VALUE))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void validationErrorsUseItToo() throws Exception {
        mockMvc.perform(post("/notebooks").with(user("someone")).contentType("application/json").content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.fields").value(1));
    }
}
