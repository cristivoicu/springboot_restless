package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.skill.SkillCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.skill.SkillUpdateModel;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration proof (backend-nunti integration, Phase 1 item 2) for the {@code @Id}-on-a-{@code
 * @MappedSuperclass} processor fix - see {@code Skill}/{@code
 * ro.cristivoicu.springbootrestless.example.entity.shared.BaseEntity}'s own javadoc for why this
 * fixture exists at all, and {@code RestlessEntityProcessorInheritedIdTest} (processor module)
 * for the isolated compile-time proof. This test exercises the same fix through a real compiled
 * {@code SkillRepository extends SpecificationRepository<Skill, UUID>}, a real generated {@code
 * SkillRestlessResource}, and a real HTTP round trip - not just the processor's own in-memory
 * compilation harness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SkillInheritedIdTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCrudCycleWorksWithAnIdInheritedFromAMappedSuperclass() throws Exception {
        SkillCreateModel create = new SkillCreateModel();
        create.setName("Welding");

        String createResponse = mockMvc.perform(post("/skills")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Welding"))
                .andReturn().getResponse().getContentAsString();

        String rawId = objectMapper.readTree(createResponse).get("id").asText();
        // Round-trips as an actual UUID, not a number - proves the generated repository/resource
        // really resolved BaseEntity's inherited @Id type, not some fallback default.
        UUID id = UUID.fromString(rawId);
        assertThat(id).isNotNull();

        mockMvc.perform(get("/skills/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rawId))
                .andExpect(jsonPath("$.name").value("Welding"));

        SkillUpdateModel update = new SkillUpdateModel();
        update.setName("Advanced welding");
        mockMvc.perform(put("/skills/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Advanced welding"));

        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[?(@.id=='" + rawId + "')]").exists());

        mockMvc.perform(delete("/skills/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/skills/{id}", id)).andExpect(status().isNotFound());
    }
}
