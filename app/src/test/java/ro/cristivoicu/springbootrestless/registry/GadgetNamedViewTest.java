package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: a named view ({@code summary}, see {@code GadgetRestlessResource}) is reachable as its
 * own extra route beyond the default {@code findOne}, 404s for an unknown id the same way
 * {@code findOne} does, and goes through the same {@code AuthorizationGuard} check. Previously
 * untested anywhere in the reactor - see {@code GadgetRestlessResource#getNamedViews}'s javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GadgetNamedViewTest {

    private static final String SCOPE_HEADER = "X-Scope-LastName";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void summaryViewReturnsTheEntity() throws Exception {
        long id = createGadget("Ada", "Lovelace", "ada@example.com");

        mockMvc.perform(get("/gadgets-dynamic/{id}/summary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
    }

    @Test
    void unknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/gadgets-dynamic/{id}/summary", 999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void guardDenialIsForbidden() throws Exception {
        long id = createGadget("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/gadgets-dynamic/{id}/summary", id)
                        .header(SCOPE_HEADER, "SomeoneElse"))
                .andExpect(status().isForbidden());
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
