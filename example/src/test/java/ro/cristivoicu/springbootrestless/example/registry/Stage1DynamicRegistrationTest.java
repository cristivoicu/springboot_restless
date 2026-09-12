package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 1/2 proof: a route registered at runtime via {@code RequestMappingHandlerMapping
 * .registerMapping(...)} (originally proved by hand in Stage 1, now performed generically by
 * {@link RestlessRegistrar} for every {@code @RestlessResource} bean) is reachable through
 * DispatcherServlet exactly like a normal {@code @RestController} route, and
 * {@code RestlessResourceHandler.findOne}'s manual id-conversion + entity-mapping works.
 * <p>
 * {@code /employees-dynamic} now goes through a real {@code CerbosAuthorizationGuard} - {@code
 * @WithMockUser} pre-authenticates as an unconditionally-allowed "admin" (see {@code
 * policies/employee.yaml}) so this stays a pure routing/mapping test, not an authorization one;
 * {@link CerbosBackedTest} supplies the real PDP that guard now calls on every request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
class Stage1DynamicRegistrationTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dynamicRouteServesTheSameEmployeeAsTheHandWrittenRoute() throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName("Katherine");
        create.setLastName("Johnson");
        create.setEmail("katherine@example.com");

        String response = mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        // same entity, reached through the dynamically registered route instead of
        // the hand-written EmployeeReadController
        mockMvc.perform(get("/employees-dynamic/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Katherine"))
                .andExpect(jsonPath("$.lastName").value("Johnson"));

        mockMvc.perform(get("/employees-dynamic/{id}", 999_999L))
                .andExpect(status().isNotFound());
    }
}
