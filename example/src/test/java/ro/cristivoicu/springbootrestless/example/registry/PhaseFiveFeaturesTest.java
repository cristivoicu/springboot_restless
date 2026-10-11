package ro.cristivoicu.springbootrestless.example.registry;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.cristivoicu.springbootrestless.example.entity.shape.Circle;
import ro.cristivoicu.springbootrestless.example.entity.shape.Square;
import ro.cristivoicu.springbootrestless.example.entity.shape.store.ShapeStore;
import ro.cristivoicu.springbootrestless.test.RestlessOwnershipContract;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 end to end: partial bulk with deduplication ({@code Note}), {@code collectionGet = LIST}
 * ({@code Attachment}), polymorphic create, validation groups and a custom repository
 * ({@code Shape}), and the ownership contract kit. Not {@code @Transactional}: partial bulk commits
 * row by row.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PhaseFiveFeaturesTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShapeStore shapes;

    private RequestPostProcessor alice;
    private RequestPostProcessor bob;

    @BeforeEach
    void users() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alice = user("alice-" + run);
        bob = user("bob-" + run);
    }

    @Test
    void aPartialBulkKeepsTheGoodRowsReportsTheBadOnesAndSkipsDuplicates() throws Exception {
        long notebook = createNotebook(alice);
        mockMvc.perform(post("/notebooks/{nb}/notes", notebook).with(alice).contentType("application/json")
                .content("{\"text\":\"Existing\"}")).andExpect(status().isCreated());

        mockMvc.perform(post("/notebooks/{nb}/notes/bulk", notebook).with(alice).contentType("application/json")
                        .content("[{\"text\":\"First\"},{\"text\":\"\"},{\"text\":\"first \"},{\"text\":\"existing\"},{\"text\":\"Second\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].index").value(1))
                .andExpect(jsonPath("$.items[0].text").value("First"))
                .andExpect(jsonPath("$.items[1].text").value("Second"));
        mockMvc.perform(get("/notebooks/{nb}/notes/list", notebook).with(alice)).andExpect(jsonPath("$.length()").value(3));

        // ?dedupe=false: duplicates are created like any other row.
        mockMvc.perform(post("/notebooks/{nb}/notes/bulk?dedupe=false", notebook).with(alice).contentType("application/json")
                .content("[{\"text\":\"First\"}]")).andExpect(jsonPath("$.created").value(1));

        // Partial never means "skip a security check": someone else's notebook is still denied outright.
        mockMvc.perform(post("/notebooks/{nb}/notes/bulk", notebook).with(bob).contentType("application/json")
                .content("[{\"text\":\"Sneaky\"}]")).andExpect(status().isNotFound());
        mockMvc.perform(get("/notebooks/{nb}/notes/list", notebook).with(alice)).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void aListDefaultResourceAnswersTheBareCollectionUrlWithAnArray() throws Exception {
        long notebook = createNotebook(alice);
        mockMvc.perform(multipart("/notebooks/{nb}/attachments", notebook).with(alice)
                .file(new MockMultipartFile("file", "a.txt", "text/plain", new byte[3]))).andExpect(status().isCreated());

        mockMvc.perform(get("/notebooks/{nb}/attachments", notebook).with(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].filename").value("a.txt"));
        mockMvc.perform(get("/notebooks/{nb}/attachments/list", notebook).with(alice)).andExpect(jsonPath("$.length()").value(1));
        // A PAGE-default resource keeps the old shape.
        mockMvc.perform(get("/notebooks").with(alice)).andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void aPolymorphicCreateBuildsTheSubclassTheModelNames() throws Exception {
        long circle = JsonPath.<Number>read(mockMvc.perform(post("/shapes").contentType("application/json")
                        .content("{\"kind\":\"circle\",\"name\":\"Sun\",\"radius\":2.5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id").longValue();
        long square = JsonPath.<Number>read(mockMvc.perform(post("/shapes").contentType("application/json")
                        .content("{\"kind\":\"square\",\"name\":\"Box\",\"side\":4}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id").longValue();

        assertThat(shapes.findById(circle)).get().isInstanceOfSatisfying(Circle.class, c -> assertThat(c.getRadius()).isEqualTo(2.5));
        assertThat(shapes.findById(square)).get().isInstanceOf(Square.class);
    }

    @Test
    void validationGroupsLetOneModelBeStricterOnCreateThanOnUpdate() throws Exception {
        mockMvc.perform(post("/shapes").contentType("application/json").content("{\"kind\":\"circle\",\"radius\":1}"))
                .andExpect(status().isBadRequest());
        long id = JsonPath.<Number>read(mockMvc.perform(post("/shapes").contentType("application/json")
                        .content("{\"kind\":\"circle\",\"name\":\"Moon\",\"radius\":1}"))
                .andReturn().getResponse().getContentAsString(), "$.id").longValue();
        mockMvc.perform(put("/shapes/{id}", id).contentType("application/json").content("{\"kind\":\"circle\",\"radius\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void theOwnershipContractHoldsForNotebooksAndNotes() throws Exception {
        RestlessOwnershipContract.of(mockMvc, "/notebooks")
                .owner(alice).stranger(bob)
                .createBody("{\"title\":\"Contract\"}")
                .updateBody("{\"title\":\"Taken\"}")
                .denialStatus(HttpStatus.NOT_FOUND)
                .verify();

        RestlessOwnershipContract.of(mockMvc, "/notebooks/{notebookId}/notes")
                .pathVariable("notebookId", createNotebook(alice))
                .owner(alice).stranger(bob)
                .createBody("{\"text\":\"Private\"}")
                .updateBody("{\"text\":\"Changed\"}")
                .verify();
    }

    private long createNotebook(RequestPostProcessor who) throws Exception {
        String response = mockMvc.perform(post("/notebooks").with(who).contentType("application/json")
                .content("{\"title\":\"Phase 5\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }
}
