package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 3 proof: Employee ("/employees", added in Stage 2) and Department
 * ("/departments", added purely by writing its own beans in Stage 3) both route correctly
 * through the same {@link RestlessRegistrar} with no cross-talk between resources - i.e. no
 * accidental shared mutable state in {@code RestlessResourceHandler}.
 * <p>
 * {@code @WithMockUser}+{@link CerbosBackedTest}: see {@link Stage1DynamicRegistrationTest}'s
 * javadoc - both {@code /employees} and {@code /departments} now go through a real
 * {@code CerbosAuthorizationGuard}; {@code admin} is unconditionally allowed on both policies, so
 * this stays a pure routing/isolation test, not an authorization one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
class CrossResourceIsolationTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void employeeAndDepartmentRouteIndependently() throws Exception {
        EmployeeCreateModel employee = new EmployeeCreateModel();
        employee.setFirstName("Barbara");
        employee.setLastName("Liskov");
        employee.setEmail("barbara@example.com");

        String employeeResponse = mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(employee)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long employeeId = objectMapper.readTree(employeeResponse).get("id").asLong();

        DepartmentCreateModel department = new DepartmentCreateModel();
        department.setName("Engineering");
        department.setCode("ENG");

        String departmentResponse = mockMvc.perform(post("/departments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(department)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long departmentId = objectMapper.readTree(departmentResponse).get("id").asLong();

        // each resource only sees its own data
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Barbara"));

        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].name").value("Engineering"));

        mockMvc.perform(get("/employees/{id}", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Liskov"));

        mockMvc.perform(get("/departments/{id}", departmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ENG"));
    }
}
