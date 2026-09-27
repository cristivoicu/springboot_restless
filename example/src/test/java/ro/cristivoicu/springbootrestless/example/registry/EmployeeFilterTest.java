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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: the filter DSL's {@code salaryGte}/{@code salaryLte} on {@code EmployeeSearchDto} work
 * through {@code EmployeeRestlessResource}'s own hand-written {@code getSpecification()} override
 * (via {@code RestlessSpecifications}, combined with the pre-existing {@code lastName} equality
 * filter) - the "builder inside a hand-written override" half of the filter DSL, complementing
 * {@code ProjectFilterTest}'s fully-automatic-reflection half. See {@code
 * EmployeeRestlessResource#getSpecification}'s own javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeFilterTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void salaryRangeNarrowsToTheExpectedWindow() throws Exception {
        createEmployee("Ada", "Lovelace", "ada@example.com", new BigDecimal("80000.00"));
        createEmployee("Grace", "Hopper", "grace@example.com", new BigDecimal("120000.00"));
        createEmployee("Hedy", "Lamarr", "hedy@example.com", new BigDecimal("160000.00"));

        mockMvc.perform(get("/employees").with(admin()).param("salaryGte", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/employees").with(admin()).param("salaryLte", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].lastName").value("Lovelace"));

        // Combined with the pre-existing plain-equality lastName filter, in the same
        // hand-written getSpecification() override.
        mockMvc.perform(get("/employees").with(admin())
                        .param("salaryGte", "100000")
                        .param("lastName", "Hopper"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Grace"));
    }

    private void createEmployee(String firstName, String lastName, String email, BigDecimal salary) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);
        create.setSalary(salary);

        mockMvc.perform(post("/employees")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }
}
