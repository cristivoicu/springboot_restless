package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.Gadget;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetRepository;
import ro.cristivoicu.springbootrestless.fixtures.gizmo.GizmoCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 3 ("Unbounded reads"): {@code findEmbeddedList} ({@code
 * RestlessEmbedResolver}, see {@code GizmoEmbedTest}) was the one unbounded read {@code
 * restless.list.max-size} didn't already cap - an {@code @RestlessEmbed(many = true)} field
 * returns however many rows join, with no limit at all.
 */
@SpringBootTest(properties = "restless.list.max-size=2")
@AutoConfigureMockMvc
@Transactional
class EmbeddedListCapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GadgetRepository gadgetRepository;

    @Test
    void findEmbeddedListIsCappedByTheListMaxSizeProperty() throws Exception {
        long gizmoId = createGizmo("Acme");
        for (int i = 0; i < 4; i++) {
            Gadget gadget = new Gadget();
            gadget.setFirstName("First" + i);
            gadget.setLastName("Acme"); // joins on Gizmo.name == Gadget.lastName, see GizmoEmbedTest
            gadget.setEmail("gadget" + i + "@example.com");
            gadgetRepository.save(gadget);
        }

        mockMvc.perform(get("/gizmos/{id}", gizmoId).param("expand", "gadgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gadgets.length()").value(2));
    }

    private long createGizmo(String name) throws Exception {
        GizmoCreateModel create = new GizmoCreateModel();
        create.setName(name);
        create.setCode("CODE");
        String response = mockMvc.perform(post("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
