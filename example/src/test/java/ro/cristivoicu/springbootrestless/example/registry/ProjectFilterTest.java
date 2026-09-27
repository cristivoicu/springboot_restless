package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectCreateModel;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: the filter DSL's {@code createdDateGte}/{@code createdDateLte} on {@code
 * ProjectSearchDto} work against a real business entity via the fully-automatic path (no
 * hand-written {@code getSpecification()} at all - {@code Project} is compile-time generated) -
 * and specifically against {@code createdDate}, a field declared on {@code
 * AbstractAuditableEntity} (a shared {@code @MappedSuperclass}), not on {@code Project} itself,
 * proving {@code RestlessResourceHandler#entityPropertyNames}'s class-hierarchy walk actually
 * reaches it. See {@code ProjectSearchDto}'s own javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFilterTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdDateRangeNarrowsToTheExpectedWindow() throws Exception {
        long apollo = createProject("Apollo");
        // Guards against both projects landing in the same clock tick - same idiom
        // ProjectAuthorizationGuardTest's own auditing test already uses.
        Thread.sleep(10);
        long gemini = createProject("Gemini");

        Instant apolloCreatedDate = createdDateOf(apollo);
        Instant geminiCreatedDate = createdDateOf(gemini);

        mockMvc.perform(get("/projects").with(admin()).param("createdDateGte", geminiCreatedDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].name").value("Gemini"));

        mockMvc.perform(get("/projects").with(admin()).param("createdDateLte", apolloCreatedDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].name").value("Apollo"));

        // Combined - both created within [apollo, gemini] - matches both.
        mockMvc.perform(get("/projects").with(admin())
                        .param("createdDateGte", apolloCreatedDate.toString())
                        .param("createdDateLte", geminiCreatedDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private Instant createdDateOf(long id) throws Exception {
        String response = mockMvc.perform(get("/projects/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Instant.parse(objectMapper.readTree(response).get("createdDate").asString());
    }

    private long createProject(String name) throws Exception {
        ProjectCreateModel create = new ProjectCreateModel();
        create.setName(name);
        create.setDepartmentCode("ENG");

        String response = mockMvc.perform(post("/projects")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }
}
