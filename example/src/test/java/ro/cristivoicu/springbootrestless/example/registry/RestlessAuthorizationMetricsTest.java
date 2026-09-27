package ro.cristivoicu.springbootrestless.example.registry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof, in this app rather than just {@code app}'s own generic fixture-based test, that a real
 * Cerbos-driven denial increments {@code restless.authorization.denials} - {@code
 * spring-boot-starter-actuator} is already an {@code example} dependency (it backs {@code
 * CerbosHealthIndicator}), so {@code RestlessAuthorizationMetrics}'s {@code
 * @ConditionalOnClass(MeterRegistry.class)} bean is already active with zero extra wiring here.
 * Reuses the same manager-out-of-scope denial {@code EmployeeAuthorizationGuardTest} already
 * proves returns 403 - this test only adds the metrics assertion on top of that same request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestlessAuthorizationMetricsTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void managerOutOfScopeDenialIncrementsTheCounter() throws Exception {
        long grace = createEmployee("Grace", "Hopper", "grace@example.com");
        double before = denialCount();

        mockMvc.perform(get("/employees/{id}", grace).with(manager("Lovelace")))
                .andExpect(status().isForbidden());

        assertThat(denialCount()).isEqualTo(before + 1);
    }

    private double denialCount() {
        Counter counter = meterRegistry.find("restless.authorization.denials")
                .tag("resource", "Employee")
                .tag("action", "READ_ONE")
                .tag("hook", "canAccess")
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private long createEmployee(String firstName, String lastName, String email) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        String response = mockMvc.perform(post("/employees")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }

    private static RequestPostProcessor manager(String scopedLastName) {
        return jwt()
                .jwt(builder -> builder.claim("scopedLastName", scopedLastName))
                .authorities(new SimpleGrantedAuthority("manager"));
    }
}
