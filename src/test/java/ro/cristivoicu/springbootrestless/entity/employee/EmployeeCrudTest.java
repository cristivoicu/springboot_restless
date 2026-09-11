package ro.cristivoicu.springbootrestless.entity.employee;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stage 0 proof-of-concept: hand-wired Create/Read/Update/Delete controllers
 * for one entity, exercising all five verbs end to end against H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenReadThenUpdateThenDelete() throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName("Ada");
        create.setLastName("Lovelace");
        create.setEmail("ada@example.com");

        String createResponse = mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Lovelace"));

        mockMvc.perform(get("/employees/{id}", 999_999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.body[0].firstName").value("Ada"));

        mockMvc.perform(get("/employees/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Lovelace"));

        mockMvc.perform(get("/employees/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].email").value("ada@example.com"));

        mockMvc.perform(get("/employees/select/async"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/employees").param("lastName", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        EmployeeUpdateModel update = new EmployeeUpdateModel();
        update.setFirstName("Augusta");
        update.setLastName("King");
        update.setEmail("augusta@example.com");

        mockMvc.perform(put("/employees/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Augusta"));

        mockMvc.perform(delete("/employees/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsInvalidBody() throws Exception {
        EmployeeCreateModel create = new EmployeeCreateModel();
        create.setFirstName("");
        create.setLastName("Turing");
        create.setEmail("not-an-email");

        mockMvc.perform(post("/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkDeleteRemovesGivenIds() throws Exception {
        long first = createEmployee("Grace", "Hopper", "grace@example.com");
        long second = createEmployee("Margaret", "Hamilton", "margaret@example.com");

        EmployeeDeleteModel deleteModel = new EmployeeDeleteModel();
        deleteModel.setIds(java.util.List.of(String.valueOf(first), String.valueOf(second)));

        mockMvc.perform(delete("/employees")
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
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
