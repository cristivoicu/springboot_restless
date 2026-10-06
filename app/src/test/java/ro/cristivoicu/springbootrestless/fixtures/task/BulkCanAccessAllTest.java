package ro.cristivoicu.springbootrestless.fixtures.task;

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
 * Ground rules Phase 2 item 10 ("Bulk performance"): {@code updateBulk} now fetches every
 * target via one {@code findAllById} and checks them all via one {@code canAccessAll} call
 * instead of looping {@code findOne}/{@code checkCanAccess} per id - this proves the batched
 * path is still correct: one denied item still fails the whole batch, and the write never
 * partially applies. Deliberately no {@code @Transactional} on this test, same reasoning as
 * {@code RowLevelWriteAuthorizationTest}: a denial's rollback is only observable from a
 * genuinely separate, later request/transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BulkCanAccessAllTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void oneItemFailingThePreImageCheckFailsTheWholeBatchedBulkUpdate() throws Exception {
        long ownedByA = createTaskOwnedBy("A", "First");
        long ownedByB = createTaskOwnedBy("B", "Second");

        // The pre-image check (now one batched findAllById + canAccessAll instead of a per-id
        // findOne/checkCanAccess loop - Ground rules Phase 2 item 10) must deny the whole batch
        // because A doesn't own the second target, even though the first update is otherwise
        // perfectly legitimate.
        String body = """
                {
                  "%d": {"title": "First (renamed)", "ownerUsername": "A"},
                  "%d": {"title": "Second (renamed)", "ownerUsername": "B"}
                }
                """.formatted(ownedByA, ownedByB);

        mockMvc.perform(put("/tasks/bulk")
                        .header(TaskAuthorizationGuard.USER_HEADER, "A")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tasks/{id}", ownedByA).header(TaskAuthorizationGuard.USER_HEADER, "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First"));
    }

    private long createTaskOwnedBy(String owner, String title) throws Exception {
        TaskCreateModel create = new TaskCreateModel();
        create.setTitle(title);
        create.setOwnerUsername(owner);
        String response = mockMvc.perform(post("/tasks")
                        .header(TaskAuthorizationGuard.USER_HEADER, owner)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
