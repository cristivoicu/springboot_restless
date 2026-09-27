package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.GizmoCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: Gadget ("/gadgets-dynamic") and Gizmo ("/gizmos") both route correctly through the
 * same {@link RestlessRegistrar} with no cross-talk between resources - i.e. no accidental
 * shared mutable state in {@code RestlessResourceHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossResourceIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void gadgetAndGizmoRouteIndependently() throws Exception {
        GadgetCreateModel gadget = new GadgetCreateModel();
        gadget.setFirstName("Barbara");
        gadget.setLastName("Liskov");
        gadget.setEmail("barbara@example.com");

        String gadgetResponse = mockMvc.perform(post("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(gadget)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long gadgetId = objectMapper.readTree(gadgetResponse).get("id").asLong();

        GizmoCreateModel gizmo = new GizmoCreateModel();
        gizmo.setName("Engineering");
        gizmo.setCode("ENG");

        String gizmoResponse = mockMvc.perform(post("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(gizmo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long gizmoId = objectMapper.readTree(gizmoResponse).get("id").asLong();

        // each resource only sees its own data
        mockMvc.perform(get("/gadgets-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Barbara"));

        mockMvc.perform(get("/gizmos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].name").value("Engineering"));

        mockMvc.perform(get("/gadgets-dynamic/{id}", gadgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Liskov"));

        mockMvc.perform(get("/gizmos/{id}", gizmoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ENG"));
    }
}
