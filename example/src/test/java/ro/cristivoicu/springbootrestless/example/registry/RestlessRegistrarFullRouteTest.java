package ro.cristivoicu.springbootrestless.example.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeCreateModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeDeleteModel;
import ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeUpdateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 2 proof: every one of the nine routes {@code RestlessRegistrar} registers dynamically
 * for {@code EmployeeRestlessResource} at "/employees" behaves the same as the
 * hand-written Stage-0 controllers at "/employees" (see {@code EmployeeCrudTest}) - full route
 * parity, not just the one route Stage 1 proved.
 * <p>
 * {@code @WithMockUser}+{@link CerbosBackedTest}: see {@link Stage1DynamicRegistrationTest}'s
 * javadoc - same reasoning, this stays a routing-parity test, not an authorization one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "demo-admin", authorities = "admin")
class RestlessRegistrarFullRouteTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allNineRoutesWorkThroughTheDynamicMechanism() throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName("Radia");
        create.setLastName("Perlman");
        create.setEmail("radia@example.com");

        String createResponse = mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Radia"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Perlman"));

        mockMvc.perform(get("/employees/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Radia"));

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Radia"));

        mockMvc.perform(get("/employees/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].email").value("radia@example.com"));

        mockMvc.perform(get("/employees/select/async"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/employees").param("lastName", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        EmployeeUpdateModel update = new EmployeeUpdateModel();
        update.setFirstName("Radia");
        update.setLastName("Updated");
        update.setEmail("radia.updated@example.com");

        mockMvc.perform(put("/employees/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Updated"));

        mockMvc.perform(delete("/employees/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void bulkDeleteWorksThroughTheDynamicMechanism() throws Exception {
        long first = createEmployee("Grace", "Hopper", "grace2@example.com");
        long second = createEmployee("Hedy", "Lamarr", "hedy@example.com");

        EmployeeDeleteModel deleteModel = new EmployeeDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(first), String.valueOf(second)));

        mockMvc.perform(post("/employees/bulk-delete")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteModel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees/{id}", first)).andExpect(status().isNotFound());
        mockMvc.perform(get("/employees/{id}", second)).andExpect(status().isNotFound());
    }

    private long createEmployee(String firstName, String lastName, String email) throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        String response = mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
