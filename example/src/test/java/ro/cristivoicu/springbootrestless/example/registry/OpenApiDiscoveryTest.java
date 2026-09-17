package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Was {@code OpenApiDiscoverySpikeTest} - the spike found that springdoc's own {@code
 * @RestController} scanning does <em>not</em> see routes {@code RestlessRegistrar} registers
 * dynamically, only routes backed by a real annotated controller bean. {@code
 * RestlessOpenApiCustomizer} ({@code app} module, a springdoc {@code GlobalOpenApiCustomizer})
 * fixes that - this is the real assertion-based test proving it, not just a printed finding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "demo-admin", authorities = "admin")
class OpenApiDiscoveryTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handWrittenParityRouteIsDocumented() throws Exception {
        // Unaffected baseline: springdoc's own scan already found this one before
        // RestlessOpenApiCustomizer ever existed - proves it isn't clobbered by the customizer.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/employees']").exists());
    }

    @Test
    void dynamicallyRegisteredRouteIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/employees-dynamic']").exists())
                .andExpect(jsonPath("$.paths['/employees-dynamic/{id}'].get.parameters[0].name").value("id"))
                .andExpect(jsonPath("$.paths['/employees-dynamic'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EmployeeCreateModel"))
                .andExpect(jsonPath("$.components.schemas.EmployeeCreateModel").exists());
    }

    @Test
    void compileTimeGeneratedRouteIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/projects']").exists())
                .andExpect(jsonPath("$.paths['/projects'].get.parameters[*].name")
                        .value(org.hamcrest.Matchers.hasItems("name", "departmentCode", "page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/projects'].get.responses.200.content['application/json'].schema.properties.body.items['$ref']")
                        .value("#/components/schemas/ProjectDto"));
    }

    @Test
    void namedCustomReadActionIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/employees-dynamic/actions/byEmailDomain'].get").exists());
    }

    @Test
    void disabledOperationsAreNotDocumented() throws Exception {
        // ProjectAssignmentRestlessResource excludes UPDATE (see getEnabledOperations()) - the
        // OpenAPI document has to reflect exactly what RestlessRegistrar actually registered,
        // not just every route the fixed table could theoretically produce.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/project-assignments/{id}'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/project-assignments/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/project-assignments/bulk'].put").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProjectAssignmentUpdateModel").doesNotExist());
    }
}
