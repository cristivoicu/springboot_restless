package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules item 3 ("Unbounded reads"): {@code bindSearchDto} never ran the bound {@code
 * SearchDto} through the {@code Validator} before - a {@code @Max} on {@code
 * GizmoSearchDto#quantityGte} (see its own javadoc) was silently ignored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchDtoValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void boundSearchDtoFieldValidationIsEnforced() throws Exception {
        mockMvc.perform(get("/gizmos").param("quantityGte", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void boundSearchDtoFieldWithinBoundsSucceeds() throws Exception {
        mockMvc.perform(get("/gizmos").param("quantityGte", "10"))
                .andExpect(status().isOk());
    }
}
