package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetDeleteModel;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetUpdateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proof: every one of the nine routes {@code RestlessRegistrar} registers dynamically for
 * {@code GadgetRestlessResource} at "/gadgets-dynamic" behaves the same as the hand-written
 * baseline controllers at "/gadgets" (see {@code GadgetCrudTest}) - full route parity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestlessRegistrarFullRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allNineRoutesWorkThroughTheDynamicMechanism() throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName("Radia");
        create.setLastName("Perlman");
        create.setEmail("radia@example.com");

        String createResponse = mockMvc.perform(post("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Radia"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/gadgets-dynamic/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Perlman"));

        mockMvc.perform(get("/gadgets-dynamic/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Radia"));

        mockMvc.perform(get("/gadgets-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Radia"));

        mockMvc.perform(get("/gadgets-dynamic/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].email").value("radia@example.com"));

        mockMvc.perform(get("/gadgets-dynamic/select/async"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/gadgets-dynamic").param("lastName", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        GadgetUpdateModel update = new GadgetUpdateModel();
        update.setFirstName("Radia");
        update.setLastName("Updated");
        update.setEmail("radia.updated@example.com");

        mockMvc.perform(put("/gadgets-dynamic/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Updated"));

        mockMvc.perform(delete("/gadgets-dynamic/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/gadgets-dynamic/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void bulkDeleteWorksThroughTheDynamicMechanism() throws Exception {
        long first = createGadget("Grace", "Hopper", "grace2@example.com");
        long second = createGadget("Hedy", "Lamarr", "hedy@example.com");

        GadgetDeleteModel deleteModel = new GadgetDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(first), String.valueOf(second)));

        mockMvc.perform(delete("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/gadgets-dynamic/{id}", first)).andExpect(status().isNotFound());
        mockMvc.perform(get("/gadgets-dynamic/{id}", second)).andExpect(status().isNotFound());
    }

    private long createGadget(String firstName, String lastName, String email) throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        String response = mockMvc.perform(post("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
