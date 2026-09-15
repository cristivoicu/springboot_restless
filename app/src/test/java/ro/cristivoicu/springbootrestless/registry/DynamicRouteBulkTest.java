package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetUpdateModel;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: {@code CreateDataSource#createAll}/{@code UpdateDataSource#updateAll}'s default
 * loop-the-single-item-verb behavior actually reaches real HTTP routes ({@code POST}/{@code PUT
 * {basePath}/bulk}), registered unconditionally (unlike {@code PATCH}) since every resource
 * already has a {@code CreateDataSource}/{@code UpdateDataSource}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DynamicRouteBulkTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bulkCreateInsertsEveryItemInOrder() throws Exception {
        GadgetCreateModel first = new GadgetCreateModel();
        first.setFirstName("Ada");
        first.setLastName("Lovelace");
        first.setEmail("ada@example.com");

        GadgetCreateModel second = new GadgetCreateModel();
        second.setFirstName("Grace");
        second.setLastName("Hopper");
        second.setEmail("grace@example.com");

        String body = objectMapper.writeValueAsString(List.of(first, second));

        mockMvc.perform(post("/gadgets-dynamic/bulk")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("Ada"))
                .andExpect(jsonPath("$[1].firstName").value("Grace"));

        mockMvc.perform(get("/gadgets-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void bulkCreateValidatesEveryItemBeforeCreatingAnyOfThem() throws Exception {
        GadgetCreateModel valid = new GadgetCreateModel();
        valid.setFirstName("Ada");
        valid.setLastName("Lovelace");
        valid.setEmail("ada@example.com");

        GadgetCreateModel invalid = new GadgetCreateModel();
        invalid.setFirstName(""); // fails @NotBlank
        invalid.setLastName("Hopper");
        invalid.setEmail("grace@example.com");

        String body = objectMapper.writeValueAsString(List.of(valid, invalid));

        mockMvc.perform(post("/gadgets-dynamic/bulk")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        // nothing was created - not even the valid first item
        mockMvc.perform(get("/gadgets-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void bulkUpdateAppliesEachEntryToItsOwnId() throws Exception {
        long first = createGadget("Ada", "Lovelace", "ada@example.com");
        long second = createGadget("Grace", "Hopper", "grace@example.com");

        GadgetUpdateModel firstUpdate = new GadgetUpdateModel();
        firstUpdate.setFirstName("Ada");
        firstUpdate.setLastName("Lovelace-Updated");
        firstUpdate.setEmail("ada@example.com");

        GadgetUpdateModel secondUpdate = new GadgetUpdateModel();
        secondUpdate.setFirstName("Grace");
        secondUpdate.setLastName("Hopper-Updated");
        secondUpdate.setEmail("grace@example.com");

        ObjectNode body = objectMapper.createObjectNode();
        body.set(String.valueOf(first), objectMapper.valueToTree(firstUpdate));
        body.set(String.valueOf(second), objectMapper.valueToTree(secondUpdate));

        ArrayNode response = (ArrayNode) objectMapper.readTree(
                mockMvc.perform(put("/gadgets-dynamic/bulk")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(body)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertLastNamesContain(response, "Lovelace-Updated", "Hopper-Updated");

        mockMvc.perform(get("/gadgets-dynamic/{id}", first))
                .andExpect(jsonPath("$.lastName").value("Lovelace-Updated"));
        mockMvc.perform(get("/gadgets-dynamic/{id}", second))
                .andExpect(jsonPath("$.lastName").value("Hopper-Updated"));
    }

    private void assertLastNamesContain(ArrayNode response, String... expected) {
        List<String> actual = response.valueStream().map(node -> node.get("lastName").asString()).toList();
        for (String name : expected) {
            org.assertj.core.api.Assertions.assertThat(actual).contains(name);
        }
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
