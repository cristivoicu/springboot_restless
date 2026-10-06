package ro.cristivoicu.springbootrestless.fixtures.widget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ground rules Phase 2 item 9 ("Conditional requests"): {@code findOne} now emits a strong
 * {@code ETag} from {@code @Version}, {@code If-None-Match} short-circuits to {@code 304}, and
 * {@code If-Match} supports {@code *}/comma-separated lists under <em>strong</em> comparison -
 * previously a weak validator (`W/"..."`) was stripped and compared as if it were strong, which
 * RFC 9110 says should never satisfy a strong-comparison precondition.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConditionalRequestsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void findOneEmitsAStrongETagFromVersion() throws Exception {
        long id = createWidget("Sprocket");

        String etag = mockMvc.perform(get("/widgets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(etag).isEqualTo("\"0\"");
    }

    @Test
    void ifNoneMatchWithTheCurrentETagReturns304WithNoBody() throws Exception {
        long id = createWidget("Sprocket");
        String etag = currentETag(id);

        mockMvc.perform(get("/widgets/{id}", id).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
    }

    @Test
    void ifNoneMatchWithAStaleETagReturns200WithTheFullBody() throws Exception {
        long id = createWidget("Sprocket");

        mockMvc.perform(get("/widgets/{id}", id).header(HttpHeaders.IF_NONE_MATCH, "\"999\""))
                .andExpect(status().isOk());
    }

    @Test
    void ifMatchStarAlwaysProceeds() throws Exception {
        long id = createWidget("Sprocket");

        mockMvc.perform(put("/widgets/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "*")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateNamed("Renamed"))))
                .andExpect(status().isOk());
    }

    @Test
    void ifMatchAcceptsTheCorrectValueAmongACommaSeparatedList() throws Exception {
        long id = createWidget("Sprocket");

        mockMvc.perform(put("/widgets/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "\"999\", \"0\", \"888\"")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateNamed("Renamed"))))
                .andExpect(status().isOk());
    }

    @Test
    void ifMatchWithOnlyAWeakValidatorIsRejectedUnderStrongComparisonEvenIfTheValueMatches() throws Exception {
        long id = createWidget("Sprocket");

        mockMvc.perform(put("/widgets/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateNamed("Renamed"))))
                .andExpect(status().isPreconditionFailed());
    }

    private static WidgetUpdateModel updateNamed(String name) {
        WidgetUpdateModel update = new WidgetUpdateModel();
        update.setName(name);
        return update;
    }

    private String currentETag(long id) throws Exception {
        return mockMvc.perform(get("/widgets/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
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
