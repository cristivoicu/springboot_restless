package ro.cristivoicu.springbootrestless.example.registry;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeDeleteModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeUpdateModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Basic CRUD smoke test against `/employees` - every verb (create, single/list/page/overview/
 * select reads, update, single/bulk delete), plus validation and not-found handling, as an
 * {@code admin} principal (unconditionally allowed by {@code policies/employee.yaml}). Row-scoping,
 * field-masking, and every other role-specific scenario live in {@link EmployeeAuthorizationGuardTest}
 * instead - this class exists purely to prove the plumbing itself works end to end against H2,
 * independent of which role is asking.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeCrudTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // See app's own WidgetLifecycleTest for why: within one @Transactional test method, @Version
    // only actually increments at flush time, which a plain save() doesn't force - real, separate
    // HTTP requests in production need no such thing, each gets its own transaction.
    @Autowired
    private EntityManager entityManager;

    @Test
    void ifMatchRejectsAStaleVersionAndAcceptsTheCurrentOne() throws Exception {
        long id = createEmployee("Ada", "Lovelace", "ada@example.com");
        long initialVersion = currentVersion(id);

        EmployeeUpdateModel update = new EmployeeUpdateModel();
        update.setFirstName("Ada");
        update.setLastName("Byron");
        update.setEmail("ada@example.com");

        // Fresh If-Match: succeeds, version advances.
        mockMvc.perform(put("/employees/{id}", id)
                        .with(admin())
                        .header(HttpHeaders.IF_MATCH, String.valueOf(initialVersion))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        entityManager.flush();
        long newVersion = currentVersion(id);
        assertThat(newVersion).isNotEqualTo(initialVersion);

        // The same (now stale) If-Match again: rejected before any write is attempted.
        mockMvc.perform(put("/employees/{id}", id)
                        .with(admin())
                        .header(HttpHeaders.IF_MATCH, String.valueOf(initialVersion))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isPreconditionFailed());

        // The current version still works.
        mockMvc.perform(put("/employees/{id}", id)
                        .with(admin())
                        .header(HttpHeaders.IF_MATCH, String.valueOf(newVersion))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    void createThenReadThenUpdateThenDelete() throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName("Ada");
        create.setLastName("Lovelace");
        create.setEmail("ada@example.com");

        String createResponse = mockMvc.perform(post("/employees")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/employees/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Lovelace"));

        mockMvc.perform(get("/employees/{id}", 999_999L).with(admin()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/employees").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Ada"));

        mockMvc.perform(get("/employees/list").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Lovelace"));

        mockMvc.perform(get("/employees/overview").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].email").value("ada@example.com"));

        mockMvc.perform(get("/employees/select/async").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/employees").with(admin()).param("lastName", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        EmployeeUpdateModel update = new EmployeeUpdateModel();
        update.setFirstName("Augusta");
        update.setLastName("King");
        update.setEmail("augusta@example.com");

        mockMvc.perform(put("/employees/{id}", id)
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"));

        mockMvc.perform(delete("/employees/{id}", id).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees/{id}", id).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsInvalidBody() throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName("");
        create.setLastName("Turing");
        create.setEmail("not-an-email");

        mockMvc.perform(post("/employees")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDeleteRemovesGivenIds() throws Exception {
        long first = createEmployee("Grace", "Hopper", "grace@example.com");
        long second = createEmployee("Margaret", "Hamilton", "margaret@example.com");

        EmployeeDeleteModel deleteModel = new EmployeeDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(first), String.valueOf(second)));

        mockMvc.perform(delete("/employees")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees/{id}", first).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(get("/employees/{id}", second).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void bulkDeleteRejectsMalformedIdWithBadRequest() throws Exception {
        EmployeeDeleteModel deleteModel = new EmployeeDeleteModel();
        deleteModel.setIds(java.util.List.of("not-a-number"));

        mockMvc.perform(delete("/employees")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isBadRequest());
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
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private long currentVersion(long id) throws Exception {
        String response = mockMvc.perform(get("/employees/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode version = objectMapper.readTree(response).get("version");
        return version.asLong();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }
}
