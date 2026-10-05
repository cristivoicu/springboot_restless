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
 * Ground rules item 3 ("Unbounded reads"): {@code restless.page.max-size} must reject a
 * client-supplied {@code size} over the configured cap with {@code 400}, not silently clamp it.
 */
@SpringBootTest(properties = "restless.page.max-size=50")
@AutoConfigureMockMvc
@Transactional
class PageSizeCapTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestingMoreThanTheConfiguredMaxPageSizeIs400() throws Exception {
        mockMvc.perform(get("/gizmos").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestingAtOrUnderTheConfiguredMaxPageSizeSucceeds() throws Exception {
        mockMvc.perform(get("/gizmos").param("size", "50"))
                .andExpect(status().isOk());
    }
}
