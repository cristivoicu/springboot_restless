package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentUpdateModel;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import ro.cristivoicu.springbootrestless.test.RestlessPageAssertions;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules Phase 3 item 17 ("Tooling"): the same full create/read/update/soft-delete/list
 * cycle {@code DepartmentDefaultCudTest} already proves against H2, run here against a real
 * {@link PostgresBackedTest} PostgreSQL container instead - identity-column id generation, the
 * {@code deleted} flag column {@code DefaultSoftDeleteDataSource} maintains, and {@code
 * excludeSoftDeleted}'s generated {@code WHERE} clause all need to actually work against a real
 * server, not just H2's emulation of one.
 * <p>
 * Tagged {@code "postgres"} - excluded from the default {@code mvn verify} run (see this
 * module's own pom, {@code excludedGroups}) so every other, H2-backed test in this suite doesn't
 * pay for a Postgres image pull/container startup on every normal run; CI's dedicated job runs
 * {@code -Dgroups=postgres} to pick up exactly this one instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
@Tag("postgres")
class DepartmentPostgresCudTest extends PostgresBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCudCycleWorksAgainstARealPostgresDatabase() throws Exception {
        DepartmentCreateModel create = new DepartmentCreateModel();
        create.setName("Research");
        create.setCode("RND");

        String createResponse = mockMvc.perform(post("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Research"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/departments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RND"));

        DepartmentUpdateModel update = new DepartmentUpdateModel();
        update.setName("Research & Development");
        update.setCode("RND2");

        mockMvc.perform(put("/departments/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Research & Development"));

        DefaultDeleteModel deleteModel = new DefaultDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(id)));

        mockMvc.perform(post("/departments/bulk-delete")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        // Soft delete, not hard: still fetchable by id, excluded from the default listing - same
        // as DepartmentDefaultCudTest's own assertion, now against the real deleted column.
        mockMvc.perform(get("/departments/{id}", id))
                .andExpect(status().isOk());
        mockMvc.perform(get("/departments"))
                .andExpect(RestlessPageAssertions.restlessPage(0, 20));
    }
}
