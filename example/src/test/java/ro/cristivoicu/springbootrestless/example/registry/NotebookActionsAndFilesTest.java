package ro.cristivoicu.springbootrestless.example.registry;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.cristivoicu.springbootrestless.example.entity.notebook.NotebookAuditHook;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 end to end, with no controller in the example app: multipart create and a binary
 * named view ({@code Attachment}), collection actions, a write action and lifecycle hooks
 * declared on {@code @RestlessEntity} - all under the same ownership rules as everything else.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotebookActionsAndFilesTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotebookAuditHook audit;

    private RequestPostProcessor alice;
    private RequestPostProcessor bob;
    private String aliceName;

    @BeforeEach
    void users() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        aliceName = "alice-" + run;
        alice = user(aliceName);
        bob = user("bob-" + run);
    }

    @Test
    void filesGoInAsMultipartAndComeBackAsTheSameBytes() throws Exception {
        long notebook = createNotebook(alice, "Scans");
        byte[] bytes = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);

        String created = mockMvc.perform(multipart("/notebooks/{nb}/attachments", notebook).with(alice)
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", bytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.size").value(bytes.length))
                .andExpect(jsonPath("$.notebookId").value(notebook))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(created, "$.id").longValue();

        MvcResult download = mockMvc.perform(get("/notebooks/{nb}/attachments/{id}/content", notebook, id).with(alice))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(bytes);

        // Someone else's file: as absent as everything else of theirs.
        mockMvc.perform(get("/notebooks/{nb}/attachments/{id}/content", notebook, id).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(multipart("/notebooks/{nb}/attachments", notebook).with(bob)
                .file(new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1}))).andExpect(status().isNotFound());
    }

    @Test
    void aMultipartCreateIsValidatedLikeAJsonOne() throws Exception {
        long notebook = createNotebook(alice, "Empty");
        mockMvc.perform(multipart("/notebooks/{nb}/attachments", notebook).with(alice).param("unrelated", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aJsonOnlyResourceRefusesMultipart() throws Exception {
        mockMvc.perform(multipart("/notebooks").with(alice).param("title", "Nope")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void collectionActionsSeeOnlyTheCallersRows() throws Exception {
        long notebook = createNotebook(alice, "Stats");
        createNotebook(alice, "More stats");
        createNotebook(bob, "Bob's");
        mockMvc.perform(multipart("/notebooks/{nb}/attachments", notebook).with(alice)
                .file(new MockMultipartFile("file", "a.txt", "text/plain", new byte[10]))).andExpect(status().isCreated());

        mockMvc.perform(get("/notebooks/actions/stats").with(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/notebooks/actions/stats").with(bob)).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/notebooks/{nb}/attachments/actions/total-size", notebook).with(alice))
                .andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.bytes").value(10));
        mockMvc.perform(get("/notebooks/{nb}/attachments/actions/total-size", notebook).with(bob)).andExpect(status().isNotFound());
    }

    @Test
    void writeActionsAndHooksAreWiredFromTheAnnotation() throws Exception {
        long notebook = createNotebook(alice, "Plans");
        assertThat(audit.entries()).contains("created " + notebook + " by " + aliceName);

        mockMvc.perform(post("/notebooks/{id}/actions/stamp", notebook).with(alice).contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Plans ✓"));
        mockMvc.perform(post("/notebooks/{id}/actions/stamp", notebook).with(bob).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/notebooks/{id}", notebook).with(alice).contentType("application/json").content("{\"title\":\"Plans 2\"}"))
                .andExpect(status().isOk());
        assertThat(audit.entries()).contains("updated " + notebook);

        // A denied delete never reaches the hook.
        mockMvc.perform(delete("/notebooks/{id}", notebook).with(bob)).andExpect(status().isNotFound());
        assertThat(audit.entries()).noneMatch(entry -> entry.startsWith("deleting " + notebook + " "));
        mockMvc.perform(delete("/notebooks/{id}", notebook).with(alice)).andExpect(status().isNoContent());
        assertThat(audit.entries()).contains("deleting " + notebook + " titled Plans 2");
    }

    private long createNotebook(RequestPostProcessor who, String title) throws Exception {
        String response = mockMvc.perform(post("/notebooks").with(who).contentType("application/json")
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }
}
