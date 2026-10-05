package ro.cristivoicu.springbootrestless.fixtures.nugget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 4 ("Sorting"): before resolving the default sort off the entity's real
 * {@code @Id} property, an unsorted request to an entity whose {@code @Id} isn't literally named
 * {@code "id"} ({@link Nugget#getCode()}) got a 400 - {@code AbstractSearchDto} always defaulted
 * to a hardcoded {@code "id"} sort, which {@code pageableOf}'s own property validation then
 * rejected as unknown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestlessEntityIdSortTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unsortedPageRequestDefaultsToTheEntitysRealIdPropertyInsteadOfA400() throws Exception {
        createNugget("b-code", "Second");
        createNugget("a-code", "First");

        mockMvc.perform(get("/nuggets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].code").value("a-code"))
                .andExpect(jsonPath("$.body[1].code").value("b-code"));
    }

    private void createNugget(String code, String label) throws Exception {
        NuggetCreateModel create = new NuggetCreateModel();
        create.setCode(code);
        create.setLabel(label);
        mockMvc.perform(post("/nuggets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());
    }
}
