package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentPatchModel;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentUpdateModel;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import ro.cristivoicu.springbootrestless.test.RestlessErrorAssertions;
import ro.cristivoicu.springbootrestless.test.RestlessPageAssertions;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 1 end-to-end proof: Department's Create/Update/Patch/Delete go entirely through
 * {@code Default*DataSource} (see {@code DepartmentRestlessResource}) - no hand-written
 * {@code *DataSource} classes exist for any of the four verbs. Validation and not-found behavior
 * must be unaffected by swapping in the default implementations.
 * <p>
 * Delete specifically goes through {@code DefaultSoftDeleteDataSource}, not the hard-deleting
 * default - {@code fullCudCycleGoesThroughTheDefaultDataSources} below proves the resulting
 * "excluded from listing, still fetchable by id" behavior directly, not just "the row is gone."
 * <p>
 * Also this app's proof that {@code spring-boot-restless-test}'s {@code
 * RestlessErrorAssertions}/{@code RestlessPageAssertions} work for a real consumer, not just
 * {@code app}'s own dogfooding - see the 400/404/page assertions below.
 * <p>
 * {@code @WithMockUser}+{@link CerbosBackedTest}: see {@link CerbosBackedTest}'s own javadoc -
 * {@code /departments} now goes through a real {@code CerbosAuthorizationGuard} too; {@code admin}
 * is unconditionally allowed by {@code policies/department.yaml}, so this stays a pure
 * CUD-defaulting test, not an authorization one.
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

        // Soft delete, not hard: still fetchable by id (RestlessResourceHandler#excludeSoftDeleted
        // deliberately never applies to findOne/update/patch, only listing/paging/custom-read) ...
        mockMvc.perform(get("/departments/{id}", id))
                .andExpect(status().isOk());

        // ... but excluded from the default paginated listing.
        mockMvc.perform(get("/departments"))
                .andExpect(RestlessPageAssertions.restlessPage(0, 20));
    }

    @Test
    void patchUpdatesOnlyTheSentField() throws Exception {
        DepartmentCreateModel create = new DepartmentCreateModel();
        create.setName("Research");
        create.setCode("RND");
        String createResponse = mockMvc.perform(post("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        DepartmentPatchModel patch = new DepartmentPatchModel();
        patch.setName("Research & Development");
        // code deliberately left null - "not sent", not "clear it".

        mockMvc.perform(patch("/departments/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Research & Development"))
                .andExpect(jsonPath("$.code").value("RND"));
    }

    @Test
    void patchOnMissingIdReturnsNotFound() throws Exception {
        DepartmentPatchModel patch = new DepartmentPatchModel();
        patch.setName("Ghost");

        mockMvc.perform(patch("/departments/{id}", 999_999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(RestlessErrorAssertions.restlessError(HttpStatus.NOT_FOUND));
    }

    @Test
    void createStillValidatesViaDefaultCreateDataSource() throws Exception {
        DepartmentCreateModel invalid = new DepartmentCreateModel();
        invalid.setName("");
        invalid.setCode("");

        mockMvc.perform(post("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(RestlessErrorAssertions.restlessError(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateStillReturnsNotFoundViaDefaultUpdateDataSource() throws Exception {
        DepartmentUpdateModel update = new DepartmentUpdateModel();
        update.setName("Ghost");
        update.setCode("GH");

        mockMvc.perform(put("/departments/{id}", 999_999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(RestlessErrorAssertions.restlessError(HttpStatus.NOT_FOUND));
    }
}
