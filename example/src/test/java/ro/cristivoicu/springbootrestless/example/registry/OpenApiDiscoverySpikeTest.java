package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spike, not a permanent guarantee: does springdoc-openapi's usual {@code @RestController}
 * scanning discover routes {@code RestlessRegistrar} registers dynamically via {@code
 * RequestMappingHandlerMapping.registerMapping(...)}, or only routes backed by an actual
 * {@code @Controller}-annotated bean? Answer printed to stdout rather than asserted on, since the
 * finding itself - not a specific pass/fail - is the point of this test; see the README for what
 * it found.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "demo-admin", authorities = "admin")
class OpenApiDiscoverySpikeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsWhetherDynamicRoutesAppearInTheGeneratedOpenApiDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean seesHandWritten = body.contains("/employees");
        boolean seesDynamic = body.contains("/employees-dynamic");
        boolean seesGeneratedProject = body.contains("/projects");

        System.out.println("=== OpenAPI discovery spike ===");
        System.out.println("Hand-written /employees present: " + seesHandWritten);
        System.out.println("Dynamic /employees-dynamic present: " + seesDynamic);
        System.out.println("Compile-time-generated /projects present: " + seesGeneratedProject);
        System.out.println("Document length: " + body.length() + " chars");
    }
}
