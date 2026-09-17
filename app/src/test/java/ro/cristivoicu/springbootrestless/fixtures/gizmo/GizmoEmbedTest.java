package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetRepository;
import ro.cristivoicu.springbootrestless.fixtures.gadget.Gadget;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code @RestlessEmbed} end to end, entirely inside {@code app} (no Cerbos): {@link
 * GizmoDto#getGadgets()} is opt-in ({@code ?expand=gadgets}), joined on {@code Gizmo.name ==
 * Gadget.lastName}, and goes through {@link
 * ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetRestlessResource}'s own header-based
 * {@code AuthorizationGuard} - the same guard {@code /gadgets-dynamic} itself is checked against.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GizmoEmbedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GadgetRepository gadgetRepository;

    private long createGizmo(String name) throws Exception {
        GizmoCreateModel create = new GizmoCreateModel();
        create.setName(name);
        create.setCode("CODE");
        String response = mockMvc.perform(post("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void unrequestedEmbedStaysAbsent() throws Exception {
        long id = createGizmo("Acme");

        mockMvc.perform(get("/gizmos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gadgets").doesNotExist());
    }

    @Test
    void requestedEmbedJoinsOnTheDeclaredFieldsAndRunsTheTargetResourcesOwnGuard() throws Exception {
        long id = createGizmo("Acme");
        gadgetRepository.save(new Gadget(null, "Wile", "Acme", "wile@acme.test"));
        gadgetRepository.save(new Gadget(null, "Road", "Acme", "road@acme.test"));
        gadgetRepository.save(new Gadget(null, "Bugs", "Other", "bugs@other.test"));

        // No X-Scope-LastName header: GadgetRestlessResource's guard is fully open, so only the
        // join filter (lastName == "Acme") restricts the result - exactly the two Acme gadgets.
        mockMvc.perform(get("/gizmos/{id}", id).param("expand", "gadgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gadgets.length()").value(2))
                .andExpect(jsonPath("$.gadgets[*].lastName", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Acme"))));

        // A scope header for a DIFFERENT lastName ANDs with the join filter - target resource's
        // own guard, not the embed mechanism, is what empties this out.
        mockMvc.perform(get("/gizmos/{id}", id).param("expand", "gadgets")
                        .header("X-Scope-LastName", "Other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gadgets.length()").value(0));
    }
}
