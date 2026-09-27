package ro.cristivoicu.springbootrestless.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof that an {@code AuthorizationGuard} denial actually increments {@code
 * restless.authorization.denials} - the one signal generic {@code http.server.requests} metrics
 * can't provide (see {@link RestlessAuthorizationMetrics}'s own javadoc). Reuses {@code
 * DoohickeyAuthorizationGuard}'s unconditional {@code DELETE_ALL} denial (already proven to
 * return 403 by {@code DoohickeyGeneratedDefaultsTest}) - this test only adds the metrics
 * assertion on top of that same request. {@code app}'s test classpath already carries a real
 * {@link MeterRegistry} bean (a {@code SimpleMeterRegistry}, auto-configured the moment
 * {@code spring-boot-starter-actuator} is present - see {@code app/pom.xml}), so {@link
 * RestlessAuthorizationMetrics}'s own {@code @ConditionalOnClass} bean genuinely activates here,
 * not a hand-wired stand-in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestlessAuthorizationMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void deniedActionIncrementsTheDenialCounter() throws Exception {
        DefaultDeleteModel deleteModel = new DefaultDeleteModel();
        deleteModel.setIds(List.of("1"));

        double before = denialCount();

        mockMvc.perform(post("/doohickeys/bulk-delete")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isForbidden());

        assertThat(denialCount()).isEqualTo(before + 1);
    }

    private double denialCount() {
        Counter counter = meterRegistry.find("restless.authorization.denials")
                .tag("resource", "Doohickey")
                .tag("action", "DELETE_ALL")
                .tag("hook", "preCheck")
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
