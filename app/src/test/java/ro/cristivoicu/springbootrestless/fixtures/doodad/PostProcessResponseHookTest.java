package ro.cristivoicu.springbootrestless.fixtures.doodad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules Phase 2 item 12 ("Fail-closed masking"): {@link DoodadAuthorizationGuard}
 * overrides {@code AuthorizationGuard.postProcessResponse} to uppercase the response DTO's
 * {@code name} - this proves {@code RestlessResourceHandler} actually runs that hook (with the
 * right entity/DTO) on {@code create}/{@code findOne}/{@code update}, the three single-entity
 * response paths this fixture's generated resource exposes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostProcessResponseHookTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createResponseIsPostProcessed() throws Exception {
        mockMvc.perform(post("/doodads")
                        .contentType("application/json")
                        .content("{\"name\":\"gizmo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("GIZMO"));
    }

    @Test
    void findOneResponseIsPostProcessed() throws Exception {
        long id = createDoodad("widget");

        mockMvc.perform(get("/doodads/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("WIDGET"));
    }

    @Test
    void updateResponseIsPostProcessed() throws Exception {
        long id = createDoodad("original");

        mockMvc.perform(put("/doodads/{id}", id)
                        .contentType("application/json")
                        .content("{\"name\":\"renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("RENAMED"));
    }

    private long createDoodad(String name) throws Exception {
        String response = mockMvc.perform(post("/doodads")
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
