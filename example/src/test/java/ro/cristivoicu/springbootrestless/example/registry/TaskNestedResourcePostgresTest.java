package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.task.TaskCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same nested-resource round trip {@link TaskNestedResourceTest} already proves against H2,
 * run here against a real {@link PostgresBackedTest} PostgreSQL container instead - the dotted
 * {@code project.id} parent-field predicate is a real JPA {@code join}/foreign-key comparison
 * that needs to actually work against a real server, not just H2's emulation of one. Tagged
 * {@code "postgres"} - see {@code DepartmentPostgresCudTest}'s own javadoc for why.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
@Tag("postgres")
class TaskNestedResourcePostgresTest extends PostgresBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void nestedCreateListAndCrossParentIsolationWorkAgainstARealPostgresDatabase() throws Exception {
        long apollo = createProject("Apollo");
        long gemini = createProject("Gemini");

        TaskCreateModel create = new TaskCreateModel();
        create.setTitle("Apollo task");
        String createResponse = mockMvc.perform(post("/projects/{projectId}/tasks", apollo)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/projects/{projectId}/tasks", apollo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/projects/{projectId}/tasks", gemini))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", gemini, taskId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", apollo, taskId))
                .andExpect(status().isOk());
    }

    private long createProject(String name) throws Exception {
        ProjectCreateModel create = new ProjectCreateModel();
        create.setName(name);
        create.setDepartmentCode("ENG");

        String response = mockMvc.perform(post("/projects")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
