package ro.cristivoicu.springbootrestless.fixtures.gadget;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Hand-wired Create/Read/Update/Delete controllers for {@link Gadget}, exercising all five
 * verbs end to end against H2 - the parity-testing baseline the dynamic mechanism's tests
 * (elsewhere in {@code registry/}) compare against.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GadgetCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenReadThenUpdateThenDelete() throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName("Ada");
        create.setLastName("Lovelace");
        create.setEmail("ada@example.com");

        String createResponse = mockMvc.perform(post("/gadgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/gadgets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Lovelace"));

        mockMvc.perform(get("/gadgets/{id}", 999_999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/gadgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Ada"));

        mockMvc.perform(get("/gadgets/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Lovelace"));

        mockMvc.perform(get("/gadgets/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].email").value("ada@example.com"));

        mockMvc.perform(get("/gadgets/select/async"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/gadgets").param("lastName", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        GadgetUpdateModel update = new GadgetUpdateModel();
        update.setFirstName("Augusta");
        update.setLastName("King");
        update.setEmail("augusta@example.com");

        mockMvc.perform(put("/gadgets/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"));

        mockMvc.perform(delete("/gadgets/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/gadgets/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsInvalidBody() throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName("");
        create.setLastName("Turing");
        create.setEmail("not-an-email");

        mockMvc.perform(post("/gadgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDeleteRemovesGivenIds() throws Exception {
        long first = createGadget("Grace", "Hopper", "grace@example.com");
        long second = createGadget("Margaret", "Hamilton", "margaret@example.com");

        GadgetDeleteModel deleteModel = new GadgetDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(first), String.valueOf(second)));

        mockMvc.perform(delete("/gadgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/gadgets/{id}", first)).andExpect(status().isNotFound());
        mockMvc.perform(get("/gadgets/{id}", second)).andExpect(status().isNotFound());
    }

    @Test
    void bulkDeleteRejectsMalformedIdWithBadRequest() throws Exception {
        GadgetDeleteModel deleteModel = new GadgetDeleteModel();
        deleteModel.setIds(java.util.List.of("not-a-number"));

        mockMvc.perform(delete("/gadgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isBadRequest());
    }

    private long createGadget(String firstName, String lastName, String email) throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        String response = mockMvc.perform(post("/gadgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
