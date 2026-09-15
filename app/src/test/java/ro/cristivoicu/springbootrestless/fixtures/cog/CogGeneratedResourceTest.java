package ro.cristivoicu.springbootrestless.fixtures.cog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proof: {@link Cog} sets {@code @RestlessEntity(operations = {READ_ONE, READ_LIST, READ_PAGE})}
 * - {@code CogRestlessResource} (compile-time generated, same as {@code SprocketRestlessResource})
 * only registers the three read routes; {@code create}/{@code update}/{@code delete*} are never
 * reachable at all, even though {@link CogCreateModel}/{@link CogUpdateModel} still exist (the
 * naming-convention requirement isn't relaxed - see {@code RestlessEntity#operations}'s javadoc).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CogGeneratedResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readRoutesWork() throws Exception {
        mockMvc.perform(get("/cogs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cogs/list"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cogs/{id}", 1))
                .andExpect(status().isNotFound()); // no Cog with id 1 - route itself is reachable
    }

    @Test
    void createRouteIsNotRegistered() throws Exception {
        CogCreateModel create = new CogCreateModel();
        create.setName("Restless");
        create.setTeeth(12);

        mockMvc.perform(post("/cogs")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void updateRouteIsNotRegistered() throws Exception {
        CogUpdateModel update = new CogUpdateModel();
        update.setName("Restless");
        update.setTeeth(12);

        mockMvc.perform(put("/cogs/{id}", 1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deleteRoutesAreNotRegistered() throws Exception {
        mockMvc.perform(delete("/cogs/{id}", 1))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/cogs"))
                .andExpect(status().isMethodNotAllowed());
    }
}
