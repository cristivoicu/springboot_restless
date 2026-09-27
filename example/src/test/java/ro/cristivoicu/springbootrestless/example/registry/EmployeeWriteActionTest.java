package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.AddCertificationRequest;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.GiveRaiseRequest;
import ro.cristivoicu.springbootrestless.example.entity.employee.JobTitle;
import ro.cristivoicu.springbootrestless.example.entity.employee.PromoteRequest;
import ro.cristivoicu.springbootrestless.example.entity.employee.RecordAchievementRequest;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: {@code EmployeeRestlessResource}'s four named write actions - {@code promote}, {@code
 * giveRaise}, {@code addCertification}, {@code recordAchievement} - are reachable as their own
 * routes, go through the same real Cerbos-backed {@code AuthorizationGuard} every other Employee
 * action does (see {@code EmployeeAuthorizationGuardTest}, the same policy file), mutate and
 * persist rather than just returning a computed response, and enforce the one invariant each
 * exists for - the actual point of a write action over a full-replace {@code PUT} (see {@code
 * JobTitle}/{@code GiveRaiseRequest}/{@code Certification}'s own javadoc for which invariant each
 * one is).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeWriteActionTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void promoteMovesToAHigherRank() throws Exception {
        long id = createEmployee(admin(), "Ada", "Lovelace", "ada@example.com");

        mockMvc.perform(post("/employees/{id}/actions/promote", id).with(manager("Lovelace"))
                        .contentType("application/json")
                        .content(promoteRequest(JobTitle.ENGINEER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("ENGINEER"));
    }

    @Test
    void promoteToTheSameOrLowerRankIsRejected() throws Exception {
        long id = createEmployee(admin(), "Grace", "Hopper", "grace@example.com");

        // New hires start at ASSOCIATE (see EmployeeCreateDataSource) - "promoting" to the same
        // rank is not a promotion at all, exactly the illegal transition this action exists to
        // reject (see JobTitle#isPromotionFrom).
        mockMvc.perform(post("/employees/{id}/actions/promote", id).with(manager("Hopper"))
                        .contentType("application/json")
                        .content(promoteRequest(JobTitle.ASSOCIATE)))
                .andExpect(status().isConflict());
    }

    @Test
    void promoteOutOfScopeIsForbidden() throws Exception {
        long id = createEmployee(admin(), "Hedy", "Lamarr", "hedy@example.com");

        mockMvc.perform(post("/employees/{id}/actions/promote", id).with(manager("SomeoneElse"))
                        .contentType("application/json")
                        .content(promoteRequest(JobTitle.ENGINEER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void giveRaiseIncreasesSalaryByThePercentage() throws Exception {
        long id = createEmployee(admin(), "Katherine", "Johnson", "katherine@example.com", new BigDecimal("100000.00"));

        mockMvc.perform(post("/employees/{id}/actions/giveRaise", id).with(manager("Johnson"))
                        .contentType("application/json")
                        .content(giveRaiseRequest("10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary").value(110000.00));
    }

    @Test
    void giveRaiseAboveTheCapIsRejected() throws Exception {
        long id = createEmployee(admin(), "Dorothy", "Vaughan", "dorothy@example.com", new BigDecimal("100000.00"));

        mockMvc.perform(post("/employees/{id}/actions/giveRaise", id).with(manager("Vaughan"))
                        .contentType("application/json")
                        .content(giveRaiseRequest("25")))
                .andExpect(status().isConflict());
    }

    @Test
    void giveRaiseWithNoSalaryOnRecordIsRejected() throws Exception {
        long id = createEmployee(admin(), "Mary", "Jackson", "mary@example.com");

        mockMvc.perform(post("/employees/{id}/actions/giveRaise", id).with(manager("Jackson"))
                        .contentType("application/json")
                        .content(giveRaiseRequest("5")))
                .andExpect(status().isConflict());
    }

    @Test
    void giveRaiseWithNonPositivePercentageIsBadRequest() throws Exception {
        long id = createEmployee(admin(), "Radia", "Perlman", "radia@example.com", new BigDecimal("100000.00"));

        mockMvc.perform(post("/employees/{id}/actions/giveRaise", id).with(manager("Perlman"))
                        .contentType("application/json")
                        .content(giveRaiseRequest("0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addCertificationAppendsToTheList() throws Exception {
        long id = createEmployee(admin(), "Margaret", "Hamilton", "margaret@example.com");

        mockMvc.perform(post("/employees/{id}/actions/addCertification", id).with(manager("Hamilton"))
                        .contentType("application/json")
                        .content(certificationRequest("AWS Certified Solutions Architect", 2024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certifications[0].name").value("AWS Certified Solutions Architect"))
                .andExpect(jsonPath("$.certifications[0].yearEarned").value(2024));
    }

    @Test
    void addingTheSameCertificationTwiceIsRejected() throws Exception {
        long id = createEmployee(admin(), "Annie", "Easley", "annie@example.com");

        mockMvc.perform(post("/employees/{id}/actions/addCertification", id).with(manager("Easley"))
                        .contentType("application/json")
                        .content(certificationRequest("PMP", 2023)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/employees/{id}/actions/addCertification", id).with(manager("Easley"))
                        .contentType("application/json")
                        .content(certificationRequest("PMP", 2024)))
                .andExpect(status().isConflict());
    }

    @Test
    void recordAchievementAppendsToTheList() throws Exception {
        long id = createEmployee(admin(), "Joan", "Clarke", "joan@example.com");

        mockMvc.perform(post("/employees/{id}/actions/recordAchievement", id).with(manager("Clarke"))
                        .contentType("application/json")
                        .content(achievementRequest("Employee of the year", 2025)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievements[0].title").value("Employee of the year"))
                .andExpect(jsonPath("$.achievements[0].year").value(2025));
    }

    @Test
    void adminHasUnconditionalAccessToEveryWriteAction() throws Exception {
        long id = createEmployee(admin(), "Rear", "Hopper", "rear@example.com");

        mockMvc.perform(post("/employees/{id}/actions/promote", id).with(admin())
                        .contentType("application/json")
                        .content(promoteRequest(JobTitle.ENGINEER)))
                .andExpect(status().isOk());
    }

    private String promoteRequest(JobTitle newJobTitle) throws Exception {
        PromoteRequest request = new PromoteRequest();
        request.setNewJobTitle(newJobTitle);
        return objectMapper.writeValueAsString(request);
    }

    private String giveRaiseRequest(String percentage) throws Exception {
        GiveRaiseRequest request = new GiveRaiseRequest();
        request.setPercentage(new BigDecimal(percentage));
        return objectMapper.writeValueAsString(request);
    }

    private String certificationRequest(String name, int yearEarned) throws Exception {
        AddCertificationRequest request = new AddCertificationRequest();
        request.setName(name);
        request.setYearEarned(yearEarned);
        return objectMapper.writeValueAsString(request);
    }

    private String achievementRequest(String title, int year) throws Exception {
        RecordAchievementRequest request = new RecordAchievementRequest();
        request.setTitle(title);
        request.setYear(year);
        return objectMapper.writeValueAsString(request);
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
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("admin"));
    }

    private static RequestPostProcessor manager(String scopedLastName) {
        return jwt()
                .jwt(builder -> builder.claim("scopedLastName", scopedLastName).claim("canViewSalary", true))
                .authorities(new SimpleGrantedAuthority("manager"));
    }
}
