package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.assignment.ProjectAssignmentCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectCreateModel;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof of {@link ro.cristivoicu.springbootrestless.example.entity.assignment.ProjectAssignment}
 * as the many-to-many between {@code Project} and {@code Employee}: a real, independently
 * paginated {@code @RestlessEntity} resource, not a {@code @RestlessEmbed} list on either side -
 * see its own javadoc for why. Covers the two things that actually matter at "1,000-10,000 team
 * members" scale: the bulk-create path validates and rejects a whole batch atomically (one bad
 * row rolls back every good one in it, courtesy of {@code RestlessResourceHandler#createBulk}'s
 * {@code @Transactional}), and membership is queryable/paginated from either direction
 * ({@code ?projectId=}/{@code ?employeeId=}) rather than embedded wholesale anywhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectAssignmentTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void managerCanAssignAnEmployeeToAProject() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");

        mockMvc.perform(post("/project-assignments").with(manager())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(assignment(project, dana))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(project))
                .andExpect(jsonPath("$.employeeId").value(dana))
                .andExpect(jsonPath("$.departmentCode").value("ENG"));
    }

    @Test
    void duplicateAssignmentIsRejectedWithConflict() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        createAssignment(admin(), project, dana);

        mockMvc.perform(post("/project-assignments").with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(assignment(project, dana))))
                .andExpect(status().isConflict());
    }

    @Test
    void bulkCreateAddsManyEmployeesToOneProjectInOneRequest() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        List<ProjectAssignmentCreateModel> batch = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long employee = createEmployee(admin(), "Employee", "Number" + i, "employee" + i + "@example.com", "ENG");
            batch.add(assignment(project, employee));
        }

        mockMvc.perform(post("/project-assignments/bulk").with(manager())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25));

        mockMvc.perform(get("/project-assignments").with(admin())
                        .param("projectId", String.valueOf(project))
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void bulkCreateRollsBackTheWholeBatchWhenOneRowIsInvalid() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        long fox = createEmployee(admin(), "Fox", "Mulder", "fox@example.com", "ENG");

        List<ProjectAssignmentCreateModel> batch = List.of(
                assignment(project, dana),
                assignment(project, fox),
                assignment(project, 999_999L)); // no such employee

        mockMvc.perform(post("/project-assignments/bulk").with(manager())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isBadRequest());

        // Neither Dana's nor Fox's (valid!) assignment was left behind - the whole batch rolled
        // back, not just the one bad row skipped.
        mockMvc.perform(get("/project-assignments").with(admin()).param("projectId", String.valueOf(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void employeeCanQueryTheirOwnAssignmentsAcrossProjects() throws Exception {
        long apollo = createProject(admin(), "Apollo", "ENG");
        long gemini = createProject(admin(), "Gemini", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        createAssignment(admin(), apollo, dana);
        createAssignment(admin(), gemini, dana);

        mockMvc.perform(get("/project-assignments").with(employee("dana@example.com"))
                        .param("employeeId", String.valueOf(dana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void employeeOnlySeesAssignmentsInTheirOwnDepartment() throws Exception {
        long engProject = createProject(admin(), "Apollo", "ENG");
        long salesProject = createProject(admin(), "Orion", "SALES");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        long fox = createEmployee(admin(), "Fox", "Mulder", "fox@example.com", "SALES");
        createAssignment(admin(), engProject, dana);
        createAssignment(admin(), salesProject, fox);

        // Dana's own department is ENG - she sees the ENG assignment when listing broadly, not
        // the SALES one, even though nothing in the query itself scopes by department explicitly.
        mockMvc.perform(get("/project-assignments").with(employee("dana@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].departmentCode").value("ENG"));
    }

    @Test
    void employeeCannotAssignAnyone() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");

        mockMvc.perform(post("/project-assignments").with(employee("dana@example.com"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(assignment(project, dana))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRemovesAnAssignment() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        long assignmentId = createAssignment(admin(), project, dana);

        mockMvc.perform(delete("/project-assignments/{id}", assignmentId).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/project-assignments").with(admin()).param("projectId", String.valueOf(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void updateRouteIsNotRegistered() throws Exception {
        long project = createProject(admin(), "Apollo", "ENG");
        long dana = createEmployee(admin(), "Dana", "Scully", "dana@example.com", "ENG");
        long assignmentId = createAssignment(admin(), project, dana);

        // getEnabledOperations() excludes UPDATE - PUT shares its path with the registered GET,
        // so this comes back 405 (a real method-not-allowed on a real route), not 404.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/project-assignments/{id}", assignmentId).with(admin())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    private ProjectAssignmentCreateModel assignment(long projectId, long employeeId) {
        ProjectAssignmentCreateModel create = new ProjectAssignmentCreateModel();
        create.setProjectId(projectId);
        create.setEmployeeId(employeeId);
        return create;
    }

    private long createAssignment(RequestPostProcessor authentication, long projectId, long employeeId) throws Exception {
        String response = mockMvc.perform(post("/project-assignments").with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(assignment(projectId, employeeId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createProject(RequestPostProcessor authentication, String name, String departmentCode) throws Exception {
        ProjectCreateModel create = new ProjectCreateModel();
        create.setName(name);
        create.setDepartmentCode(departmentCode);

        String response = mockMvc.perform(post("/projects").with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createEmployee(RequestPostProcessor authentication, String firstName, String lastName,
                                 String email, String departmentCode) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);
        create.setDepartmentCode(departmentCode);

        String response = mockMvc.perform(post("/employees").with(authentication)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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
