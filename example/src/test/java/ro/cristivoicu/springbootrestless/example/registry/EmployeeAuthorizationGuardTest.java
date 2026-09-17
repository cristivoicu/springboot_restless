package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeDeleteModel;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proof: {@code EmployeeRestlessResource}'s real {@code CerbosAuthorizationGuard<Employee>},
 * backed by a real Cerbos PDP ({@link CerbosBackedTest}) evaluating this module's own {@code
 * src/main/resources/policies/employee.yaml}, exercises all three {@code AuthorizationGuard} hook
 * points against actual policy decisions - not the header-based stand-in this test used to cover.
 * {@code admin} is unconditionally allowed; {@code manager} is scoped to {@code lastName ==
 * <their own scopedLastName claim>} via a real translated Cerbos query plan.
 * <p>
 * The {@code salary} tests below cover a different mechanism on the same policy file: {@code
 * CerbosFieldMasker} field-level masking (via {@code @CerbosHiddenField} on {@code
 * EmployeeDto.salary}), driven by the {@code view} action's output rather than row-level
 * access - see {@code EmployeeMapper} and {@code policies/employee.yaml}'s {@code view} rule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeAuthorizationGuardTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesEveryEmployeeUnscoped() throws Exception {
        createEmployee(admin(), "Ada", "Lovelace", "ada@example.com");
        createEmployee(admin(), "Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/employees").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void managerListAndPageReadsAreScopedToTheirOwnLastName() throws Exception {
        createEmployee(admin(), "Ada", "Lovelace", "ada@example.com");
        createEmployee(admin(), "Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/employees").with(manager("Lovelace")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].lastName").value("Lovelace"));

        mockMvc.perform(get("/employees/list").with(manager("Lovelace")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void managerOutOfScopeDirectFetchIsForbidden() throws Exception {
        long grace = createEmployee(admin(), "Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/employees/{id}", grace).with(manager("Lovelace")))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerBulkDeleteFailsFastLeavingTheInScopeEntityUntouched() throws Exception {
        long ada = createEmployee(admin(), "Ada", "Lovelace", "ada@example.com");
        long grace = createEmployee(admin(), "Grace", "Hopper", "grace@example.com");

        EmployeeDeleteModel deleteModel = new EmployeeDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(ada), String.valueOf(grace)));

        mockMvc.perform(delete("/employees")
                        .with(manager("Lovelace"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isForbidden());

        // nothing was deleted - not even Ada, whose lastName matches the scope
        mockMvc.perform(get("/employees/{id}", ada).with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/employees/{id}", grace).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void adminSeesSalaryOnASingleFetch() throws Exception {
        long id = createEmployee(admin(), "Ada", "Lovelace", "ada@example.com", new BigDecimal("95000"));

        String response = mockMvc.perform(get("/employees/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("salary").decimalValue()).isEqualByComparingTo("95000");
    }

    @Test
    void managerWithoutCanViewSalaryGetsItMaskedOnASingleFetch() throws Exception {
        long id = createEmployee(admin(), "Ada", "Lovelace", "ada@example.com", new BigDecimal("95000"));

        String response = mockMvc.perform(get("/employees/{id}", id).with(manager("Lovelace", false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("salary").isNull()).isTrue();
    }

    @Test
    void managerWithCanViewSalarySeesTheRealValueOnASingleFetch() throws Exception {
        long id = createEmployee(admin(), "Ada", "Lovelace", "ada@example.com", new BigDecimal("95000"));

        String response = mockMvc.perform(get("/employees/{id}", id).with(manager("Lovelace", true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("salary").decimalValue()).isEqualByComparingTo("95000");
    }

    @Test
    void managerWithoutCanViewSalaryGetsItMaskedOnAListReadTooViaBatchedMasking() throws Exception {
        createEmployee(admin(), "Ada", "Lovelace", "ada@example.com", new BigDecimal("95000"));

        // /list uses EmployeeMapper.map(List<Employee>) - CerbosFieldMasker.maskAll's one
        // batched RPC path, not the single-entity one the tests above exercise.
        String response = mockMvc.perform(get("/employees/list").with(manager("Lovelace", false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get(0).get("salary").isNull()).isTrue();
    }

    private long createEmployee(RequestPostProcessor authentication, String firstName, String lastName, String email) throws Exception {
        return createEmployee(authentication, firstName, lastName, email, null);
    }

    private long createEmployee(RequestPostProcessor authentication, String firstName, String lastName,
                                 String email, BigDecimal salary) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);
        create.setSalary(salary);

        String response = mockMvc.perform(post("/employees")
                        .with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }

    private static RequestPostProcessor manager(String scopedLastName) {
        return manager(scopedLastName, true);
    }

    private static RequestPostProcessor manager(String scopedLastName, boolean canViewSalary) {
        return jwt()
                .jwt(builder -> builder.claim("scopedLastName", scopedLastName).claim("canViewSalary", canViewSalary))
                .authorities(new SimpleGrantedAuthority("manager"));
    }
}
