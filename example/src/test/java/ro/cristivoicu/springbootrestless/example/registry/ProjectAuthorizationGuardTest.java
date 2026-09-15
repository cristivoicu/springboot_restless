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
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proof: {@code Project}'s real {@code CerbosAuthorizationGuard<Project>} (wrapped by {@code
 * ProjectAuthorizationGuardBean} and wired into the compile-time-generated {@code
 * ProjectRestlessResource} via {@code @RestlessEntity(authorizationGuard = ...)}), backed by a
 * real Cerbos PDP ({@link CerbosBackedTest}) evaluating this module's own {@code
 * src/main/resources/policies/project.yaml}. {@code admin}/{@code manager} are unconditionally
 * allowed; {@code employee} is scoped to {@code departmentCode == <their own department>}, where
 * "their own department" is resolved from the authenticated principal's own {@code Employee} row
 * (matched by the JWT {@code email} claim) rather than a JWT claim Keycloak itself issues - see
 * {@code ProjectAuthorizationGuardBean}'s javadoc for why that needs {@code
 * principalAttributesExtender} rather than a plain resource-attributes mapper.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectAuthorizationGuardTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeesEveryProjectAcrossDepartments() throws Exception {
        createProject(admin(), "Apollo", "ENG");
        createProject(admin(), "Orion", "SALES");

        mockMvc.perform(get("/projects").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void managerSeesEveryProjectAcrossDepartmentsUnconditionally() throws Exception {
        createProject(admin(), "Apollo", "ENG");
        createProject(admin(), "Orion", "SALES");

        mockMvc.perform(get("/projects").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void employeeOnlySeesProjectsInTheirOwnDepartment() throws Exception {
        createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        createProject(admin(), "Apollo", "ENG");
        createProject(admin(), "Orion", "SALES");

        mockMvc.perform(get("/projects").with(employee("dana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].name").value("Apollo"));
    }

    @Test
    void employeeOutOfDepartmentDirectFetchIsForbidden() throws Exception {
        createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        long salesProject = createProject(admin(), "Orion", "SALES");

        mockMvc.perform(get("/projects/{id}", salesProject).with(employee("dana@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeInDepartmentDirectFetchSucceeds() throws Exception {
        createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        long engProject = createProject(admin(), "Apollo", "ENG");

        mockMvc.perform(get("/projects/{id}", engProject).with(employee("dana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apollo"));
    }

    @Test
    void employeeWithNoMatchingEmployeeRecordSeesNothing() throws Exception {
        createProject(admin(), "Apollo", "ENG");

        // "ghost@example.com" has no Employee row at all - ownDepartmentAttribute() resolves
        // nothing, so the row-scoping condition never matches any department.
        mockMvc.perform(get("/projects").with(employee("ghost@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void employeeCannotCreate() throws Exception {
        ProjectCreateModel create = new ProjectCreateModel();
        create.setName("Skunkworks");
        create.setDepartmentCode("ENG");

        mockMvc.perform(post("/projects")
                        .with(employee("dana@example.com"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isForbidden());
    }

    @Test
    void overriddenCreateDataSourceRunsInsteadOfTheDefault() throws Exception {
        // ProjectCreateDataSource defaults a blank description instead of leaving it blank,
        // unlike the default's plain field-by-field copy - proves the escape hatch still applies
        // now that it's injected by hand instead of via @RestlessEntity.
        ProjectCreateModel create = new ProjectCreateModel();
        create.setName("Apollo");
        create.setDescription("");
        create.setDepartmentCode("ENG");

        mockMvc.perform(post("/projects")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("No description provided"));
    }

    @Test
    void createStillValidates() throws Exception {
        ProjectCreateModel invalid = new ProjectCreateModel();
        invalid.setName("");
        invalid.setDepartmentCode("ENG");

        mockMvc.perform(post("/projects")
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    private long createProject(RequestPostProcessor authentication, String name, String departmentCode) throws Exception {
        ProjectCreateModel create = new ProjectCreateModel();
        create.setName(name);
        create.setDepartmentCode(departmentCode);

        String response = mockMvc.perform(post("/projects")
                        .with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createEmployee(RequestPostProcessor authentication, String firstName, String lastName,
                                 String email, String departmentCode) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);
        create.setDepartmentCode(departmentCode);

        mockMvc.perform(post("/employees-dynamic")
                        .with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }

    private static RequestPostProcessor manager() {
        return jwt().authorities(new SimpleGrantedAuthority("manager"));
    }

    private static RequestPostProcessor employee(String email) {
        return jwt().jwt(builder -> builder.claim("email", email)).authorities(new SimpleGrantedAuthority("employee"));
    }
}
