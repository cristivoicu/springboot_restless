package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetRenameRequest;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: a named custom write action ({@code rename}, see {@code GadgetRestlessResource}) is
 * reachable as its own extra route beyond the fixed nine, mutates and persists (not just
 * returns), goes through the same {@code AuthorizationGuard}/validation pipeline every other
 * mutation does, and 404s for an unknown id the same way {@code findOne} does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GadgetWriteActionTest {

    private static final String SCOPE_HEADER = "X-Scope-LastName";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void renameMutatesAndPersistsTheEntity() throws Exception {
        long id = createGadget("Ada", "Lovelace", "ada@example.com");

        GadgetRenameRequest rename = new GadgetRenameRequest();
        rename.setNewLastName("Byron");

        mockMvc.perform(post("/gadgets-dynamic/{id}/actions/rename", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(rename)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Byron"));

        // Persisted, not just returned - a fresh GET sees the same new value.
        mockMvc.perform(get("/gadgets-dynamic/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Byron"));
    }

    @Test
    void guardDenialIsForbidden() throws Exception {
        long id = createGadget("Grace", "Hopper", "grace@example.com");

        GadgetRenameRequest rename = new GadgetRenameRequest();
        rename.setNewLastName("Murray");

        mockMvc.perform(post("/gadgets-dynamic/{id}/actions/rename", id)
                        .header(SCOPE_HEADER, "SomeoneElse")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(rename)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankRequestFieldIsRejectedWithBadRequest() throws Exception {
        long id = createGadget("Hedy", "Lamarr", "hedy@example.com");

        GadgetRenameRequest rename = new GadgetRenameRequest();
        rename.setNewLastName("");

        mockMvc.perform(post("/gadgets-dynamic/{id}/actions/rename", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(rename)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void undeclaredActionNameReturnsNotFound() throws Exception {
        long id = createGadget("Katherine", "Johnson", "katherine@example.com");

        mockMvc.perform(post("/gadgets-dynamic/{id}/actions/noSuchAction", id)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownIdReturnsNotFound() throws Exception {
        GadgetRenameRequest rename = new GadgetRenameRequest();
        rename.setNewLastName("Whoever");

        mockMvc.perform(post("/gadgets-dynamic/{id}/actions/rename", 999_999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(rename)))
                .andExpect(status().isNotFound());
    }

    private long createGadget(String firstName, String lastName, String email) throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        String response = mockMvc.perform(post("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
