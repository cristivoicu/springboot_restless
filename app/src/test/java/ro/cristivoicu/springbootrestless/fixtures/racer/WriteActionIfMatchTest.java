package ro.cristivoicu.springbootrestless.fixtures.racer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules Phase 2 item 9: {@code If-Match} is now optionally honored on named write
 * actions too - previously {@code writeAction()} never checked it at all, regardless of whether
 * the client sent one. "Optional" means unchanged no-op-when-absent semantics (the first test
 * below), not a new mandatory precondition.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WriteActionIfMatchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writeActionWithNoIfMatchHeaderStillSucceeds() throws Exception {
        long id = createRacer("original");

        mockMvc.perform(post("/racers/{id}/actions/rename", id)
                        .contentType("application/json")
                        .content("{\"newName\":\"renamed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void writeActionWithAMatchingIfMatchSucceeds() throws Exception {
        long id = createRacer("original");

        mockMvc.perform(post("/racers/{id}/actions/rename", id)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType("application/json")
                        .content("{\"newName\":\"renamed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void writeActionWithAStaleIfMatchIsRejected() throws Exception {
        long id = createRacer("original");

        mockMvc.perform(post("/racers/{id}/actions/rename", id)
                        .header(HttpHeaders.IF_MATCH, "\"999\"")
                        .contentType("application/json")
                        .content("{\"newName\":\"renamed\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    private long createRacer(String name) throws Exception {
        RacerCreateModel create = new RacerCreateModel();
        create.setName(name);
        String response = mockMvc.perform(post("/racers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
