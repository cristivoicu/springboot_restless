package ro.cristivoicu.springbootrestless.fixtures.gizmo;

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
 * Proof: {@code GizmoRestlessResource}'s unoverridden default {@code getSpecification()} (the
 * only fixture in the reactor that actually uses it, rather than a hand-written override -
 * {@code Gadget}/{@code Employee} both override it) recognizes the filter DSL's operator-suffix
 * fields on {@code GizmoSearchDto} - {@code nameLike}/{@code quantityGte}/{@code quantityLte}/
 * {@code codeNe}/{@code codeIn} - alongside its original plain-equality {@code name} field,
 * unchanged. See {@code docs/design/filter-dsl.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GizmoFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void plainEqualityFieldIsUnaffected() throws Exception {
        createGizmo("Research", "RND", 5);
        createGizmo("Development", "DEV", 10);

        mockMvc.perform(get("/gizmos/list").param("name", "Research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("RND"));
    }

    @Test
    void likeSuffixDoesAContainsMatch() throws Exception {
        createGizmo("Research", "RND", 5);
        createGizmo("Development", "DEV", 10);
        createGizmo("Search Engine", "SEARCH", 15);

        mockMvc.perform(get("/gizmos/list").param("nameLike", "earch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void gteAndLteSuffixesFilterARange() throws Exception {
        createGizmo("A", "A", 5);
        createGizmo("B", "B", 10);
        createGizmo("C", "C", 15);

        mockMvc.perform(get("/gizmos/list").param("quantityGte", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/gizmos/list").param("quantityLte", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Combined - the intersection (just "B").
        mockMvc.perform(get("/gizmos/list").param("quantityGte", "10").param("quantityLte", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("B"));
    }

    @Test
    void neSuffixExcludesTheGivenValue() throws Exception {
        createGizmo("A", "A", 5);
        createGizmo("B", "B", 10);

        mockMvc.perform(get("/gizmos/list").param("codeNe", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("B"));
    }

    @Test
    void inSuffixMatchesAnyOfTheRepeatedValues() throws Exception {
        createGizmo("A", "A", 5);
        createGizmo("B", "B", 10);
        createGizmo("C", "C", 15);

        mockMvc.perform(get("/gizmos/list").param("codeIn", "A", "C"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private long createGizmo(String name, String code, int quantity) throws Exception {
        GizmoCreateModel create = new GizmoCreateModel();
        create.setName(name);
        create.setCode(code);
        create.setQuantity(quantity);

        String response = mockMvc.perform(post("/gizmos")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
