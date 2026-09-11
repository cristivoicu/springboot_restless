package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.entity.department.DepartmentCreateModel;
import ro.cristivoicu.springbootrestless.entity.employee.EmployeeCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 3 proof: Employee ("/employees-dynamic", added in Stage 2) and Department
 * ("/departments", added purely by writing its own beans in Stage 3) both route correctly
 * through the same {@link RestlessRegistrar} with no cross-talk between resources - i.e. no
 * accidental shared mutable state in {@code RestlessResourceHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossResourceIsolationTest {

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

        String employeeResponse = mockMvc.perform(post("/employees-dynamic")
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
        mockMvc.perform(get("/employees-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Barbara"));

        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].name").value("Engineering"));

        mockMvc.perform(get("/employees-dynamic/{id}", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Liskov"));

        mockMvc.perform(get("/departments/{id}", departmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ENG"));
    }
}
