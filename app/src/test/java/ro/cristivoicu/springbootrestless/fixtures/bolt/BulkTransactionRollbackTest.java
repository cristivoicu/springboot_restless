package ro.cristivoicu.springbootrestless.fixtures.bolt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code RestlessResourceHandler#createBulk}'s transaction wrapper is real, not just the
 * up-front validation {@code EmployeeCreateDataSource}/{@code ProjectAssignmentCreateDataSource}
 * (and this class's own earlier self) happen to do before ever touching the database - {@link
 * BoltCreateDataSource} deliberately <em>saves first, then throws</em> for the third item, so a
 * naive "validated everything up front" test could never catch a regression here (that's exactly
 * how the previous, insufficient version of this proof - {@code
 * ProjectAssignmentTest#bulkCreateRollsBackTheWholeBatchWhenOneRowIsInvalid} - passed without the
 * transaction wrapper actually mattering: its own {@code createAll} override never reaches {@code
 * saveAll()} on a bad row at all).
 * <p>
 * Deliberately <b>not</b> {@code @Transactional} on this test class, unlike almost every other
 * {@code MockMvc} test in this reactor: Spring Test's own transactional rollback would wrap this
 * whole test method in one outer transaction and roll everything back at the end regardless of
 * whether {@code RestlessResourceHandler#inTransaction} did anything at all - that would make
 * this test pass for the wrong reason, the same trap the test it replaces fell into. Verifying
 * within the same test method, right after the failing request, is what actually distinguishes
 * "the real per-request transaction rolled back" from "the test harness cleaned up afterwards."
 */
@SpringBootTest
@AutoConfigureMockMvc
class BulkTransactionRollbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BoltRepository boltRepository;

    @Test
    void aFailureAfterSomeRowsAreAlreadySavedRollsBackTheWholeBatch() throws Exception {
        BoltCreateModel first = new BoltCreateModel();
        first.setName("Ok1");
        BoltCreateModel second = new BoltCreateModel();
        second.setName("Ok2");
        BoltCreateModel poison = new BoltCreateModel();
        poison.setName(BoltCreateDataSource.POISON_NAME);

        try {
            mockMvc.perform(post("/bolts/bulk")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(List.of(first, second, poison))))
                    .andExpect(status().is5xxServerError());

            // Ok1 and Ok2 were genuinely INSERTed (BoltCreateDataSource.create() saves before it
            // ever checks the poison name) - if RestlessResourceHandler#inTransaction were a
            // no-op, both would still be visible right here.
            mockMvc.perform(get("/bolts/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        } finally {
            // No @Transactional test-rollback safety net here (see class javadoc) - clean up by
            // hand so a re-run of the full suite doesn't see leftover rows from a failed attempt.
            boltRepository.deleteAll();
        }
    }
}
