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
 * Ground rules item 2 ("Row-level authorization on writes"): both halves of {@code
 * TaskAuthorizationGuard}'s ownership rule must actually be enforced, not just the pre-image
 * one that already existed. Before this fix: CREATE never called {@code canAccess} at all (a
 * created row's own guard was simply never checked), and UPDATE only checked the pre-image, so a
 * client who legitimately owned a row could reassign it to someone else freely.
 * <p>
 * Deliberately no {@code @Transactional} on this test, same reasoning as {@code
 * ConcurrentUpdateRaceTest}: a denial's rollback is only observable by a later, genuinely
 * separate request/transaction - wrapping this test in one shared transaction would make an
 * uncommitted (not-yet-rolled-back) write from one request visible to a later request within
 * the very same test, which is exactly the thing being asserted doesn't happen.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RowLevelWriteAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void userACannotCreateATaskOwnedByB() throws Exception {
        TaskCreateModel create = new TaskCreateModel();
        create.setTitle("Steal this");
        create.setOwnerUsername("B");

        mockMvc.perform(post("/tasks")
                        .header(TaskAuthorizationGuard.USER_HEADER, "A")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isForbidden());

        // Rolled back, not left as a dangling row nobody but B's own requests would ever see -
        // TaskAuthorizationGuard has no scope(), so this checks by specific title rather than an
        // (unscoped) full listing, which would also include every other test's own rows.
        mockMvc.perform(get("/tasks/list").header(TaskAuthorizationGuard.USER_HEADER, "B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Steal this')]").isEmpty());
    }

    @Test
    void userACannotUpdateTheirOwnTaskToBeOwnedByB() throws Exception {
        long id = createTaskOwnedBy("A", "Mine");

        TaskUpdateModel update = new TaskUpdateModel();
        update.setTitle("Mine");
        update.setOwnerUsername("B");

        mockMvc.perform(put("/tasks/{id}", id)
                        .header(TaskAuthorizationGuard.USER_HEADER, "A")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());

        // Rolled back - still A's row, not reassigned to B.
        mockMvc.perform(get("/tasks/{id}", id).header(TaskAuthorizationGuard.USER_HEADER, "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUsername").value("A"));
    }

    @Test
    void userACanStillUpdateTheirOwnTaskWithoutChangingOwnership() throws Exception {
        long id = createTaskOwnedBy("A", "Mine");

        TaskUpdateModel update = new TaskUpdateModel();
        update.setTitle("Still mine");
        update.setOwnerUsername("A");

        mockMvc.perform(put("/tasks/{id}", id)
                        .header(TaskAuthorizationGuard.USER_HEADER, "A")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Still mine"));
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
