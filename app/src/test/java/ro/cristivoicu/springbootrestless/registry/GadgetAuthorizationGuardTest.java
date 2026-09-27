package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetDeleteModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proof: {@code GadgetRestlessResource}'s demonstration {@code AuthorizationGuard<Gadget>} - a
 * stand-in for a real principal-derived guard, scoped via the {@code X-Scope-LastName} header -
 * exercises all three hook points ({@code preCheck} is implicitly exercised too, since it's
 * always called; the demo guard just never denies it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GadgetAuthorizationGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void withoutTheHeaderBehaviorIsUnchanged() throws Exception {
        long ada = createGadget("Ada", "Lovelace", "ada@example.com");
        createGadget("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/gadgets-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/gadgets-dynamic/{id}", ada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    void withTheHeaderListAndPageReadsAreScoped() throws Exception {
        createGadget("Ada", "Lovelace", "ada@example.com");
        createGadget("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/gadgets-dynamic").header("X-Scope-LastName", "Lovelace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].lastName").value("Lovelace"));

        mockMvc.perform(get("/gadgets-dynamic/list").header("X-Scope-LastName", "Lovelace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void outOfScopeDirectFetchIsForbidden() throws Exception {
        long grace = createGadget("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/gadgets-dynamic/{id}", grace).header("X-Scope-LastName", "Lovelace"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkDeleteFailsFastLeavingTheInScopeEntityUntouched() throws Exception {
        long ada = createGadget("Ada", "Lovelace", "ada@example.com");
        long grace = createGadget("Grace", "Hopper", "grace@example.com");

        GadgetDeleteModel deleteModel = new GadgetDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(ada), String.valueOf(grace)));

        mockMvc.perform(post("/gadgets-dynamic/bulk-delete")
                        .header("X-Scope-LastName", "Lovelace")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isForbidden());

        // nothing was deleted - not even Ada, whose lastName matches the scope
        mockMvc.perform(get("/gadgets-dynamic/{id}", ada))
                .andExpect(status().isOk());
        mockMvc.perform(get("/gadgets-dynamic/{id}", grace))
                .andExpect(status().isOk());
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
