package ro.cristivoicu.springbootrestless.fixtures.widget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules Phase 2 item 13 ("Soft delete"): {@code restless.soft-delete.include-in-single-read}
 * set to {@code false} here - unlike {@code WidgetLifecycleTest}'s own {@code
 * softDeletedWidgetIsExcludedFromListingButStillFetchableById} (which runs against the default
 * {@code true} and proves a soft-deleted row stays fetchable by id), this property makes {@code
 * findOne} 404 a soft-deleted row too, same as a row that was never there. A distinct {@code
 * properties} value means Spring Boot caches this as its own, separate application context -
 * never shares state with (or pollutes) the default-context tests elsewhere in this module.
 */
@SpringBootTest(properties = "restless.soft-delete.include-in-single-read=false")
@AutoConfigureMockMvc
@Transactional
class SoftDeleteSingleReadPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void findOneAlso404sASoftDeletedRowWhenThePropertyIsDisabled() throws Exception {
        long id = createWidget("Delete me");
        mockMvc.perform(delete("/widgets/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/widgets/{id}", id))
                .andExpect(status().isNotFound());
    }

    private long createWidget(String name) throws Exception {
        WidgetCreateModel create = new WidgetCreateModel();
        create.setName(name);
        String response = mockMvc.perform(post("/widgets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
