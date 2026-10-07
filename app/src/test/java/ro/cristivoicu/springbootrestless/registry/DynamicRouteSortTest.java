package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.cristivoicu.springbootrestless.fixtures.gadget.GadgetCreateModel;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proof: {@link ro.cristivoicu.springbootrestless.models.AbstractSearchDto}'s repeatable {@code
 * sort} query param drives a real multi-field {@code Sort}, and {@code
 * RestlessResourceHandler#pageableOf} rejects an unknown property with a 400 before any query
 * runs, rather than letting Hibernate turn it into an opaque 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DynamicRouteSortTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void multiFieldSortOrdersByEachClauseInTurn() throws Exception {
        createGadget("Ada", "Zephyr", "ada@example.com");
        createGadget("Bob", "Anders", "bob@example.com");
        createGadget("Ada", "Anders", "ada2@example.com");

        // lastName asc, then firstName asc within ties: Anders/Ada, Anders/Bob, Zephyr/Ada.
        mockMvc.perform(get("/gadgets-dynamic")
                        .param("sort", "lastName,asc")
                        .param("sort", "firstName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].firstName").value("Ada"))
                .andExpect(jsonPath("$.body[0].lastName").value("Anders"))
                .andExpect(jsonPath("$.body[1].firstName").value("Bob"))
                .andExpect(jsonPath("$.body[1].lastName").value("Anders"))
                .andExpect(jsonPath("$.body[2].lastName").value("Zephyr"));
    }

    /**
     * A single {@code ?sort=lastName,asc} (one occurrence, not the repeated-param form the test
     * above uses) must bind identically - {@code "property,direction"} in one query param value
     * is {@code AbstractSearchDto}'s own documented single-field form, the same convention Spring
     * Data's own {@code Pageable} resolver uses. {@code ServletRequestDataBinder} hands a
     * single-occurrence param to the conversion service as a plain {@code String}, not a
     * one-element array; converting that string onto a {@code List<String>} target goes through
     * {@code DefaultConversionService}'s {@code StringToCollectionConverter}, which splits on
     * every comma in the whole string - turning one {@code "lastName,asc"} entry into two list
     * elements, {@code "lastName"} and {@code "asc"}, each then parsed as its own property with no
     * direction. A repeated param (the test above) never hits this path at all, since Spring binds
     * a multi-value request parameter as a real array, one list element per occurrence, with no
     * comma-splitting involved.
     */
    @Test
    void singleSortParamWithCommaAndDirectionBindsAsOneClauseNotTwo() throws Exception {
        createGadget("Ada", "Zephyr", "ada3@example.com");
        createGadget("Bob", "Anders", "bob2@example.com");

        mockMvc.perform(get("/gadgets-dynamic").param("sort", "lastName,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].lastName").value("Zephyr"))
                .andExpect(jsonPath("$.body[1].lastName").value("Anders"));
    }

    /** A leading {@code -} is accepted as shorthand for descending, same as GitHub's/many REST APIs' sort convention - {@code ?sort=-lastName} is equivalent to {@code ?sort=lastName,desc}. */
    @Test
    void leadingMinusSignMeansDescending() throws Exception {
        createGadget("Ada", "Zephyr", "ada4@example.com");
        createGadget("Bob", "Anders", "bob3@example.com");

        mockMvc.perform(get("/gadgets-dynamic").param("sort", "-lastName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].lastName").value("Zephyr"))
                .andExpect(jsonPath("$.body[1].lastName").value("Anders"));
    }

    @Test
    void unknownSortPropertyIsRejectedWithBadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/gadgets-dynamic").param("sort", "notAnActualField,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown sort property 'notAnActualField' for Gadget"));
    }

    @Test
    void unresolvableSortDirectionIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(get("/gadgets-dynamic").param("sort", "lastName,sideways"))
                .andExpect(status().isBadRequest());
    }

    private void createGadget(String firstName, String lastName, String email) throws Exception {
        GadgetCreateModel create = new GadgetCreateModel();
        create.setFirstName(firstName);
        create.setLastName(lastName);
        create.setEmail(email);

        mockMvc.perform(post("/gadgets-dynamic")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());
    }
}
