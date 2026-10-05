package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.GizmoCreateModel;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 3 ("Unbounded reads"): {@code restless.bulk.max-size} must reject a bulk
 * create carrying more items than the configured cap with {@code 400}, before any item is
 * processed.
 */
@SpringBootTest(properties = "restless.bulk.max-size=2")
@AutoConfigureMockMvc
@Transactional
class BulkSizeCapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBulkOverTheConfiguredMaxSizeIs400() throws Exception {
        mockMvc.perform(post("/gizmos/bulk")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(List.of(
                                gizmo("a"), gizmo("b"), gizmo("c")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBulkAtTheConfiguredMaxSizeSucceeds() throws Exception {
        mockMvc.perform(post("/gizmos/bulk")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(List.of(
                                gizmo("a"), gizmo("b")))))
                .andExpect(status().isOk());
    }

    private static GizmoCreateModel gizmo(String name) {
        GizmoCreateModel model = new GizmoCreateModel();
        model.setName(name);
        model.setCode("CODE");
        return model;
    }
}
