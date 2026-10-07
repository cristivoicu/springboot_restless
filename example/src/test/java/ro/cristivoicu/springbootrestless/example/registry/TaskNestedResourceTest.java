package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.task.TaskCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.task.TaskUpdateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of Feature B (nested resources) against {@code Task} ({@code
 * /projects/{projectId}/tasks}, parent field {@code "project.id"} - a dotted {@code @ManyToOne}
 * association, the harder of the two forms {@code @RestlessParent} supports; the flat-scalar form
 * is covered separately by an {@code app}-module unit test). Every scenario here mirrors the ten
 * numbered semantics in {@code RestlessParent}'s own javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
class TaskNestedResourceTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createIgnoresAnyAttemptToSetTheParentAndScopesToThePathProject() throws Exception {
        long projectId = createProject("Apollo");

        TaskCreateModel create = new TaskCreateModel();
        create.setTitle("Write mission plan");

        mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write mission plan"))
                .andExpect(jsonPath("$.done").value(false));
    }

    @Test
    void listOnlyReturnsThePathProjectsOwnTasks() throws Exception {
        long apollo = createProject("Apollo");
        long gemini = createProject("Gemini");
        createTask(apollo, "Apollo task");
        createTask(gemini, "Gemini task");

        mockMvc.perform(get("/projects/{projectId}/tasks", apollo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].title").value("Apollo task"));

        mockMvc.perform(get("/projects/{projectId}/tasks", gemini))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].title").value("Gemini task"));
    }

    @Test
    void getUpdateDeleteOfAForeignProjectsTask404s() throws Exception {
        long apollo = createProject("Apollo");
        long gemini = createProject("Gemini");
        long taskUnderApollo = createTask(apollo, "Apollo task");

        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", gemini, taskUnderApollo))
                .andExpect(status().isNotFound());

        TaskUpdateModel update = new TaskUpdateModel();
        update.setTitle("Hijacked");
        update.setDone(true);
        mockMvc.perform(put("/projects/{projectId}/tasks/{id}", gemini, taskUnderApollo)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/projects/{projectId}/tasks/{id}", gemini, taskUnderApollo))
                .andExpect(status().isNotFound());

        // Untouched - the row is still there, still Apollo's, once addressed under its own project.
        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", apollo, taskUnderApollo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Apollo task"));
    }

    @Test
    void bulkDeleteWithOneForeignTaskFailsTheWholeBatchAndDeletesNothing() throws Exception {
        long apollo = createProject("Apollo");
        long gemini = createProject("Gemini");
        long ownTask = createTask(apollo, "Own task");
        long foreignTask = createTask(gemini, "Foreign task");

        String deleteBody = "{\"ids\": [\"" + ownTask + "\", \"" + foreignTask + "\"]}";
        mockMvc.perform(post("/projects/{projectId}/tasks/bulk-delete", apollo)
                        .contentType("application/json")
                        .content(deleteBody))
                .andExpect(status().isNotFound());

        // Nothing written - the own task is still there.
        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", apollo, ownTask))
                .andExpect(status().isOk());
    }

    @Test
    void unconvertiblePathVariableIs400() throws Exception {
        mockMvc.perform(get("/projects/{projectId}/tasks", "not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonExistentProjectIsRejectedByTheGuardBeforeAnyRowIsEvenLoaded() throws Exception {
        // TaskAuthorizationGuardBean.preCheck (Feature B item 8) - cheap, once per request.
        mockMvc.perform(get("/projects/{projectId}/tasks", 999_999))
                .andExpect(status().isForbidden());
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

    private long createTask(long projectId, String title) throws Exception {
        TaskCreateModel create = new TaskCreateModel();
        create.setTitle(title);

        String response = mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
