package ro.cristivoicu.springbootrestless.fixtures.racer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 1 ("Atomic write pipeline"): a concurrent change landing between the guard
 * check and the write must surface as a conflict, never a silent overwrite. Deliberately no
 * {@code @Transactional} on this test class - each {@code mockMvc.perform(...)} call needs to
 * run its own, genuinely independent transaction against the real {@code PlatformTransactionManager}
 * for two requests to actually race at all; wrapping the whole test in one transaction (the
 * pattern every other test in this reactor uses, for rollback-based cleanup) would make both
 * "requests" share one connection/transaction, which can't race with itself.
 * <p>
 * {@code spring.jpa.open-in-view=false}, deliberately not the Spring Boot default of {@code
 * true}: OSIV binds one Hibernate {@code Session} for a request's entire lifetime regardless of
 * {@code @Transactional} boundaries, which - purely as a side effect, not by design - makes the
 * handler's separate guard-check load and the data source's own internal reload share one
 * first-level cache even in the old, unfixed code, masking the exact race this test exists to
 * catch. Production is routinely run with OSIV off (it's the generally-recommended setting -
 * see {@code RestlessResourceHandler#inReadOnlyTransaction}'s own javadoc), so this is the
 * realistic case, not an edge case.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
class ConcurrentUpdateRaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearLatches() {
        RacerAuthorizationGuard.paused = null;
        RacerAuthorizationGuard.release = null;
        RacerAuthorizationGuard.armed.set(false);
    }

    @Test
    void aConcurrentUpdateBetweenTheGuardCheckAndTheWriteYieldsAConflictNotASilentOverwrite() throws Exception {
        long id = createRacer("original");

        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RacerAuthorizationGuard.paused = paused;
        RacerAuthorizationGuard.release = release;
        RacerAuthorizationGuard.armed.set(true);

        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<MvcResult> slow = pool.submit(() -> mockMvc.perform(put("/racers/{id}", id)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(updateNamed("A"))))
                    .andReturn());

            assertThat(paused.await(5, TimeUnit.SECONDS)).isTrue();

            // Fully independent, completes while the first request is still paused mid-write.
            mockMvc.perform(put("/racers/{id}", id)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(updateNamed("B"))))
                    .andExpect(status().isOk());

            release.countDown();
            MvcResult slowResult = slow.get(5, TimeUnit.SECONDS);

            assertThat(slowResult.getResponse().getStatus()).isIn(409, 412);
        } finally {
            pool.shutdownNow();
        }

        // The committed state is B's write - never silently clobbered by the stale, conflicting one.
        mockMvc.perform(get("/racers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("B"));
    }

    private static RacerUpdateModel updateNamed(String name) {
        RacerUpdateModel update = new RacerUpdateModel();
        update.setName(name);
        return update;
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
