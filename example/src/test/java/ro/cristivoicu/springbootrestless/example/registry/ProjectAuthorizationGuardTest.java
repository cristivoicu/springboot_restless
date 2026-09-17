package ro.cristivoicu.springbootrestless.example.registry;

import jakarta.persistence.EntityManager;
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
import ro.cristivoicu.springbootrestless.example.entity.project.ProjectUpdateModel;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
    private EntityManager entityManager;

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

    @Test
    void createdDateAndLastModifiedDatePopulateAutomatically() throws Exception {
        // Project is this codebase's one AbstractAuditableEntity demo (see its own javadoc) -
        // ExampleApplication's @EnableJpaAuditing is what actually makes these populate.
        long id = createProject(admin(), "Apollo", "ENG");

        String afterCreate = mockMvc.perform(get("/projects/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant createdDate = Instant.parse(objectMapper.readTree(afterCreate).get("createdDate").asString());
        Instant firstModified = Instant.parse(objectMapper.readTree(afterCreate).get("lastModifiedDate").asString());
        assertThat(createdDate).isNotNull();
        assertThat(firstModified).isNotNull();

        Thread.sleep(10); // guards against two timestamps landing in the same clock tick
        ProjectUpdateModel update = new ProjectUpdateModel();
        update.setName("Apollo 2");
        update.setDepartmentCode("ENG");
        mockMvc.perform(put("/projects/{id}", id)
                        .with(admin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        // The whole test method runs in one shared transaction/persistence context (@Transactional
        // test rollback) - @LastModifiedDate is applied by AuditingHandler at pre-update/flush
        // time, which a plain save() doesn't force, so an explicit flush is needed here for the
        // bumped timestamp to be observable within a single test method at all. A real, separate
        // HTTP request in production needs no such thing - each gets its own transaction.
        entityManager.flush();

        String afterUpdate = mockMvc.perform(get("/projects/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant unchangedCreatedDate = Instant.parse(objectMapper.readTree(afterUpdate).get("createdDate").asString());
        Instant secondModified = Instant.parse(objectMapper.readTree(afterUpdate).get("lastModifiedDate").asString());

        assertThat(unchangedCreatedDate).isEqualTo(createdDate);
        assertThat(secondModified).isAfter(firstModified);
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

        mockMvc.perform(post("/employees")
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
