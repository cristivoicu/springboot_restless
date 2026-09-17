package ro.cristivoicu.springbootrestless.fixtures.widget;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof, through real HTTP, of the two features {@code Widget}/{@code
 * WidgetRestlessResource} exist for: the opt-in {@code If-Match} precondition ({@code
 * RestlessResourceHandler#checkIfMatch}) and {@link
 * ro.cristivoicu.springbootrestless.datasource.defaults.DefaultSoftDeleteDataSource}'s
 * flag-and-keep delete semantics. The write-time 409-on-a-genuinely-concurrent-save path (the
 * <em>other</em> half of optimistic concurrency, {@code RestlessExceptionHandler#handleOptimisticLock})
 * is proved separately in {@code RestlessExceptionHandlerOptimisticLockTest} - that one needs a
 * real stale-version race, which a single-threaded HTTP test can't produce deterministically the
 * way a direct repository-level test can.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WidgetLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // The whole test method runs inside one shared transaction/persistence context (@Transactional
    // test rollback) - Hibernate's @Version increment only actually happens at flush time, which a
    // plain save() doesn't force, so an explicit flush is needed between a write and the next read
    // for the bumped version to be observable at all within a single test method. Real, separate
    // HTTP requests in production don't need this - each gets its own transaction.
    @Autowired
    private EntityManager entityManager;

    @Test
    void ifMatchRejectsAStaleVersionAndAcceptsTheCurrentOne() throws Exception {
        long id = createWidget("Sprocket wrench");
        long initialVersion = currentVersion(id);

        WidgetUpdateModel update = new WidgetUpdateModel();
        update.setName("Sprocket wrench v2");

        // Fresh If-Match: succeeds, version advances.
        mockMvc.perform(put("/widgets/{id}", id)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(initialVersion))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        entityManager.flush();
        long newVersion = currentVersion(id);
        assertThat(newVersion).isNotEqualTo(initialVersion);

        // The same (now stale) If-Match again: rejected before any write is attempted.
        mockMvc.perform(put("/widgets/{id}", id)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(initialVersion))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isPreconditionFailed());
        assertThat(currentVersion(id)).isEqualTo(newVersion); // untouched by the rejected attempt

        // The current version still works.
        mockMvc.perform(put("/widgets/{id}", id)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(newVersion))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    void updateWithNoIfMatchHeaderIsUnaffectedByVersioning() throws Exception {
        long id = createWidget("No preconditions here");

        WidgetUpdateModel update = new WidgetUpdateModel();
        update.setName("Still works");

        mockMvc.perform(put("/widgets/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Still works"));
    }

    @Test
    void softDeletedWidgetIsExcludedFromListingButStillFetchableById() throws Exception {
        long keptId = createWidget("Keep me");
        long deletedId = createWidget("Delete me");

        mockMvc.perform(delete("/widgets/{id}", deletedId))
                .andExpect(status().isNoContent());

        // Still fetchable by id - see excludeSoftDeleted's own javadoc for why.
        mockMvc.perform(get("/widgets/{id}", deletedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deletedId));

        // Excluded from the default paginated listing.
        mockMvc.perform(get("/widgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[?(@.id == " + deletedId + ")]").doesNotExist())
                .andExpect(jsonPath("$.body[?(@.id == " + keptId + ")]").exists());
    }

    private long createWidget(String name) throws Exception {
        WidgetCreateModel create = new WidgetCreateModel();
        create.setName(name);
        String response = mockMvc.perform(post("/widgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long currentVersion(long id) throws Exception {
        String response = mockMvc.perform(get("/widgets/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode version = objectMapper.readTree(response).get("version");
        return version.asLong();
    }
}
