package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.models.DefaultDeleteModel;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proof: {@link Doohickey} declares no hand-written {@code Mapper} and no hand-written resource
 * bean at all - {@code RestlessEntityProcessor} generates both a reflective default {@code
 * Mapper} (skipping {@code @RestlessMapperExclude}d {@link DoohickeyDto} fields) and wires
 * {@link DoohickeyAuthorizationGuard} in via {@code @RestlessEntity(authorizationGuard = ...)},
 * exactly like a hand-written resource overriding {@code getAuthorizationGuard()} would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DoohickeyGeneratedDefaultsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatedMapperCopiesPlainFieldsButSkipsTheExcludedOne() throws Exception {
        DoohickeyCreateModel create = new DoohickeyCreateModel();
        create.setName("Sonic screwdriver");
        create.setSecret("42");

        String createResponse = mockMvc.perform(post("/doohickeys")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sonic screwdriver"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(createResponse).get("secret").isNull()).isTrue();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        String getResponse = mockMvc.perform(get("/doohickeys/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sonic screwdriver"))
                .andReturn().getResponse().getContentAsString();

        // Doohickey.secret really is "42" in the database (DefaultCreateDataSource's own
        // BeanUtils.copyProperties has no reason to skip it) - only the generated DoohickeyMapper
        // leaves DoohickeyDto.secret unset, because of @RestlessMapperExclude.
        assertThat(objectMapper.readTree(getResponse).get("secret").isNull()).isTrue();
    }

    @Test
    void annotationWiredGuardIsActuallyConsultedNotTheDefaultPermissiveOne() throws Exception {
        DefaultDeleteModel deleteModel = new DefaultDeleteModel();
        deleteModel.setIds(List.of("1"));

        // DoohickeyAuthorizationGuard.preCheck() denies DELETE_ALL unconditionally - a 403 here
        // (not RestlessResourceHandler's default-permissive AuthorizationGuard.allowAll()) proves
        // authorizationGuard = DoohickeyAuthorizationGuard.class on @RestlessEntity actually
        // wired this specific bean into the generated resource.
        mockMvc.perform(delete("/doohickeys")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isForbidden());
    }

    @Test
    void everythingElseStillWorksUnconditionally() throws Exception {
        DoohickeyCreateModel create = new DoohickeyCreateModel();
        create.setName("Flux capacitor");

        mockMvc.perform(post("/doohickeys")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/doohickeys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
