package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proves Create/Update/Delete go entirely through {@code DefaultCreateDataSource}/{@code
 * DefaultUpdateDataSource}/{@code DefaultDeleteDataSource} (see {@code GizmoRestlessResource}) -
 * no hand-written {@code *DataSource} classes exist for those three verbs at all. Validation and
 * not-found behavior must be unaffected by using the default implementations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GizmoDefaultCudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCudCycleGoesThroughTheDefaultDataSources() throws Exception {
        GizmoCreateModel create = new GizmoCreateModel();
        create.setName("Research");
        create.setCode("RND");

        String createResponse = mockMvc.perform(post("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Research"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/gizmos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RND"));

        GizmoUpdateModel update = new GizmoUpdateModel();
        update.setName("Research & Development");
        update.setCode("RND2");

        mockMvc.perform(put("/gizmos/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Research & Development"))
                .andExpect(jsonPath("$.code").value("RND2"));

        DefaultDeleteModel deleteModel = new DefaultDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(id)));

        mockMvc.perform(delete("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/gizmos/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createStillValidatesViaDefaultCreateDataSource() throws Exception {
        GizmoCreateModel invalid = new GizmoCreateModel();
        invalid.setName("");
        invalid.setCode("");

        mockMvc.perform(post("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStillReturnsNotFoundViaDefaultUpdateDataSource() throws Exception {
        GizmoUpdateModel update = new GizmoUpdateModel();
        update.setName("Ghost");
        update.setCode("GH");

        mockMvc.perform(put("/gizmos/{id}", 999_999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }
}
