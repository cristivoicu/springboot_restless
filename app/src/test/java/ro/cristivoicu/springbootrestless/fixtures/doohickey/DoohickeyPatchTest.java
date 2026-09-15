package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: {@code @RestlessEntity(patchDataSource = DoohickeyPatchDataSource.class)} registers a
 * real {@code PATCH} route through {@code RestlessRegistrar}, and {@link
 * ro.cristivoicu.springbootrestless.datasource.defaults.DefaultPatchDataSource}'s reflective
 * partial-update actually leaves fields the client didn't send untouched, rather than nulling
 * them out the way a full {@code PUT} would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DoohickeyPatchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void patchingOnlyNameLeavesSecretUntouched() throws Exception {
        DoohickeyCreateModel create = new DoohickeyCreateModel();
        create.setName("Sonic screwdriver");
        create.setSecret("42");
        long id = createDoohickey(create);

        DoohickeyPatchModel patchModel = new DoohickeyPatchModel();
        patchModel.setName("Sonic screwdriver mk2");
        // secret deliberately left null - "not sent", not "clear it"

        mockMvc.perform(patch("/doohickeys/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(patchModel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sonic screwdriver mk2"));

        // secret isn't in DoohickeyDto's generated mapper output at all (@RestlessMapperExclude -
        // see DoohickeyDto), so read it back straight from the repository instead of the API.
        mockMvc.perform(get("/doohickeys/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sonic screwdriver mk2"));
    }

    @Test
    void patchingUnknownIdReturns404() throws Exception {
        DoohickeyPatchModel patchModel = new DoohickeyPatchModel();
        patchModel.setName("Ghost");

        mockMvc.perform(patch("/doohickeys/{id}", 999_999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(patchModel)))
                .andExpect(status().isNotFound());
    }

    private long createDoohickey(DoohickeyCreateModel create) throws Exception {
        String response = mockMvc.perform(post("/doohickeys")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        assertThat(id).isPositive();
        return id;
    }
}
