package ro.cristivoicu.springbootrestless.example.registry;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 end to end against {@code Notebook} (direct ownership, {@code @RestlessFromPrincipal},
 * {@code @RestlessImmutable(REJECT)}) and {@code Note} (ownership via its nested parent), both
 * {@code denyAs = NOT_FOUND}. Not {@code @Transactional}: a denied create must really roll back,
 * which a test-wide transaction would hide. Each test uses fresh usernames instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotebookOwnershipTest extends CerbosBackedTest {

    @Autowired
    private MockMvc mockMvc;

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
    void theOwnerComesFromThePrincipalNeverFromTheBody() throws Exception {
        mockMvc.perform(post("/notebooks").with(alice).contentType("application/json")
                        .content("{\"title\":\"Recipes\",\"ownerUsername\":\"mallory\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUsername").value(aliceName));
    }

    @Test
    void someoneElsesNotebookLooksExactlyLikeAMissingOne() throws Exception {
        long id = createNotebook(alice, "Diary", "D-1");

        MvcResult denied = mockMvc.perform(get("/notebooks/{id}", id).with(bob)).andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mockMvc.perform(get("/notebooks/{id}", Long.MAX_VALUE).with(bob)).andExpect(status().isNotFound()).andReturn();
        assertThat(denied.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());

        String deniedPut = mockMvc.perform(put("/notebooks/{id}", id).with(bob).contentType("application/json")
                .content("{\"title\":\"Mine now\"}")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(deniedPut).contains("Entity " + id + " not found");
        mockMvc.perform(delete("/notebooks/{id}", id).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/notebooks/bulk-delete").with(bob).contentType("application/json")
                .content("{\"ids\":[\"" + id + "\"]}")).andExpect(status().isNotFound());

        // Lists are scoped in SQL - a filter naming the owner can't widen them.
        mockMvc.perform(get("/notebooks/list").with(bob)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/notebooks/list?ownerUsername=" + aliceName).with(bob)).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/notebooks/{id}", id).with(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Diary"));
    }

    @Test
    void anImmutableFieldCanBeResentButNotChanged() throws Exception {
        long id = createNotebook(alice, "Ledger", "L-1");

        mockMvc.perform(put("/notebooks/{id}", id).with(alice).contentType("application/json")
                        .content("{\"title\":\"Ledger\",\"code\":\"L-2\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/notebooks/{id}", id).with(alice).contentType("application/json")
                        .content("{\"title\":\"Ledger 2026\",\"code\":\"L-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("L-1"));
        // Omitted (null) counts as "not sent": the stored value survives the full-replace PUT.
        mockMvc.perform(put("/notebooks/{id}", id).with(alice).contentType("application/json")
                        .content("{\"title\":\"Ledger 2027\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("L-1"))
                .andExpect(jsonPath("$.ownerUsername").value(aliceName));
    }

    @Test
    void notesBelongToWhoeverOwnsTheirNotebook() throws Exception {
        long alices = createNotebook(alice, "Work", null);
        long bobs = createNotebook(bob, "Home", null);
        long note = JsonPath.<Number>read(mockMvc.perform(post("/notebooks/{nb}/notes", alices).with(alice)
                        .contentType("application/json").content("{\"text\":\"Ship it\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notebookId").value(alices))
                .andReturn().getResponse().getContentAsString(), "$.id").longValue();

        // Through Alice's notebook: Bob can neither read, list, change nor add.
        mockMvc.perform(get("/notebooks/{nb}/notes/{id}", alices, note).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/notebooks/{nb}/notes/list", alices).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/notebooks/{nb}/notes", alices).with(bob).contentType("application/json")
                .content("{\"text\":\"Sneaky\"}")).andExpect(status().isNotFound());
        // Through his own notebook, pointing at Alice's note: still not there.
        mockMvc.perform(get("/notebooks/{nb}/notes/{id}", bobs, note).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/notebooks/{nb}/notes/{id}", bobs, note).with(bob)).andExpect(status().isNotFound());

        mockMvc.perform(get("/notebooks/{nb}/notes/list", alices).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Ship it"));
        mockMvc.perform(delete("/notebooks/{nb}/notes/{id}", alices, note).with(alice)).andExpect(status().isNoContent());
    }

    @Test
    void unauthenticatedCallersGetNothing() throws Exception {
        mockMvc.perform(get("/notebooks/list")).andExpect(status().isUnauthorized());
    }

    private long createNotebook(RequestPostProcessor who, String title, String code) throws Exception {
        String body = code == null ? "{\"title\":\"" + title + "\"}" : "{\"title\":\"" + title + "\",\"code\":\"" + code + "\"}";
        String response = mockMvc.perform(post("/notebooks").with(who).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }
}
