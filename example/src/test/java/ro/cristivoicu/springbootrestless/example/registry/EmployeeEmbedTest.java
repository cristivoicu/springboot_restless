package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.department.DepartmentCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof of {@code @RestlessEmbed} on {@code EmployeeDto.department} - genuinely bounded (exactly
 * zero or one row), the shape {@code RestlessEmbed} is actually meant for. See {@code
 * ProjectAssignmentTest} for the project-membership side, which is <em>not</em> an embed - a
 * project's team is unbounded in principle, so it's its own paginated resource instead (see
 * {@code ProjectAssignment}'s javadoc for the full reasoning, and {@code RestlessEmbed}'s for the
 * general rule this follows).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeEmbedTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unrequestedEmbedStaysAbsent() throws Exception {
        long id = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");

        mockMvc.perform(get("/employees/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").doesNotExist());
    }

    @Test
    void adminSeesTheDepartmentWhenRequested() throws Exception {
        createDepartment(admin(), "Engineering", "ENG");
        long id = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");

        mockMvc.perform(get("/employees/{id}", id).with(admin()).param("expand", "department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.code").value("ENG"))
                .andExpect(jsonPath("$.department.name").value("Engineering"));
    }

    @Test
    void employeeViewingAColleagueStillSeesTheDepartment() throws Exception {
        // policies/department.yaml is role-only (no scoping) - every authenticated employee sees
        // any department, unlike policies/project-assignment.yaml's row-scoped team visibility.
        createDepartment(admin(), "Engineering", "ENG");
        createEmployee(admin(), "Fox", "Mulder", "fox@example.com", "SALES");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");

        mockMvc.perform(get("/employees/{id}", dana).with(employee("fox@example.com"))
                        .param("expand", "department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.code").value("ENG"));
    }

    private long createEmployee(RequestPostProcessor authentication, String firstName, String lastName,
                                 String email, String departmentCode) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);
        create.setDepartmentCode(departmentCode);

        String response = mockMvc.perform(post("/employees")
                        .with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createDepartment(RequestPostProcessor authentication, String name, String code) throws Exception {
        DepartmentCreateModel create = new DepartmentCreateModel();
        create.setName(name);
        create.setCode(code);

        mockMvc.perform(post("/departments")
                        .with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }

    private static RequestPostProcessor employee(String email) {
        return jwt().jwt(builder -> builder.claim("email", email)).authorities(new SimpleGrantedAuthority("employee"));
    }
}
