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
 * Proof: a named custom read action ({@code byEmailDomain}, see {@code GadgetRestlessResource})
 * is reachable as its own extra route beyond the fixed nine, with its own {@code SearchDto} and
 * query logic the default equality filter can't express.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GadgetCustomReadActionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void byEmailDomainFiltersByEmailSuffix() throws Exception {
        createGadget("Ada", "Lovelace", "ada@example.com");
        createGadget("Grace", "Hopper", "grace@other.org");

        mockMvc.perform(get("/gadgets-dynamic/actions/byEmailDomain").param("domain", "example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Ada"));

        mockMvc.perform(get("/gadgets-dynamic/actions/byEmailDomain").param("domain", "other.org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Grace"));

        mockMvc.perform(get("/gadgets-dynamic/actions/byEmailDomain").param("domain", "nowhere.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void undeclaredActionNameReturnsNotFound() throws Exception {
        mockMvc.perform(get("/gizmos/actions/noSuchAction"))
                .andExpect(status().isNotFound());
    }

    private void createGadget(String firstName, String lastName, String email) throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        mockMvc.perform(post("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());
    }
}
