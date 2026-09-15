package ro.cristivoicu.springbootrestless.example.registry;

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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 1 end-to-end proof: Department's Create/Update/Delete go entirely through
 * {@code DefaultCreateDataSource}/{@code DefaultUpdateDataSource}/{@code DefaultDeleteDataSource}
 * (see {@code DepartmentRestlessResource}) - no hand-written DataSource classes exist for those
 * three verbs at all. Validation and not-found behavior must be unaffected by swapping in the
 * default implementations.
 * <p>
 * {@code @WithMockUser}+{@link CerbosBackedTest}: see {@link Stage1DynamicRegistrationTest}'s
 * javadoc - {@code /departments} now goes through a real {@code CerbosAuthorizationGuard} too;
 * {@code admin} is unconditionally allowed by {@code policies/department.yaml}, so this stays a
 * pure CUD-defaulting test, not an authorization one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
class DepartmentDefaultCudTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCudCycleGoesThroughTheDefaultDataSources() throws Exception {
        DepartmentCreateModel create = new DepartmentCreateModel();
        create.setName("Research");
        create.setCode("RND");

        String createResponse = mockMvc.perform(post("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
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
                .andExpect(jsonPath("$.name").value("Research & Development"))
                .andExpect(jsonPath("$.code").value("RND2"));

        DefaultDeleteModel deleteModel = new DefaultDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(id)));

        mockMvc.perform(delete("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/departments/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createStillValidatesViaDefaultCreateDataSource() throws Exception {
        DepartmentCreateModel invalid = new DepartmentCreateModel();
        invalid.setName("");
        invalid.setCode("");

        mockMvc.perform(post("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStillReturnsNotFoundViaDefaultUpdateDataSource() throws Exception {
        DepartmentUpdateModel update = new DepartmentUpdateModel();
        update.setName("Ghost");
        update.setCode("GH");

        mockMvc.perform(put("/departments/{id}", 999_999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }
}
