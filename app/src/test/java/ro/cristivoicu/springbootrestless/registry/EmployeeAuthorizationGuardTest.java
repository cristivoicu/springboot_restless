package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.entity.employee.EmployeeCreateModel;
import ro.cristivoicu.springbootrestless.entity.employee.EmployeeDeleteModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 3 proof: {@code EmployeeRestlessResource}'s demonstration
 * {@code AuthorizationGuard<Employee>} - a stand-in for a real principal-derived guard, scoped
 * via the {@code X-Scope-LastName} header - exercises all three hook points ({@code preCheck}
 * is implicitly exercised too, since it's always called; the demo guard just never denies it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeAuthorizationGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void withoutTheHeaderBehaviorIsUnchanged() throws Exception {
        long ada = createEmployee("Ada", "Lovelace", "ada@example.com");
        createEmployee("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/employees-dynamic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/employees-dynamic/{id}", ada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    void withTheHeaderListAndPageReadsAreScoped() throws Exception {
        createEmployee("Ada", "Lovelace", "ada@example.com");
        createEmployee("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/employees-dynamic").header("X-Scope-LastName", "Lovelace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].lastName").value("Lovelace"));

        mockMvc.perform(get("/employees-dynamic/list").header("X-Scope-LastName", "Lovelace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void outOfScopeDirectFetchIsForbidden() throws Exception {
        long grace = createEmployee("Grace", "Hopper", "grace@example.com");

        mockMvc.perform(get("/employees-dynamic/{id}", grace).header("X-Scope-LastName", "Lovelace"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkDeleteFailsFastLeavingTheInScopeEntityUntouched() throws Exception {
        long ada = createEmployee("Ada", "Lovelace", "ada@example.com");
        long grace = createEmployee("Grace", "Hopper", "grace@example.com");

        EmployeeDeleteModel deleteModel = new EmployeeDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(ada), String.valueOf(grace)));

        mockMvc.perform(delete("/employees-dynamic")
                        .header("X-Scope-LastName", "Lovelace")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isForbidden());

        // nothing was deleted - not even Ada, whose lastName matches the scope
        mockMvc.perform(get("/employees-dynamic/{id}", ada))
                .andExpect(status().isOk());
        mockMvc.perform(get("/employees-dynamic/{id}", grace))
                .andExpect(status().isOk());
    }

    private long createEmployee(String firstName, String lastName, String email) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        String response = mockMvc.perform(post("/employees-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
