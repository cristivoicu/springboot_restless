package ro.cristivoicu.springbootrestless.test;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * "A stranger can't touch the owner's rows", checked on every route of one resource - so an
 * ownership rule is proven by one call instead of a page of hand-written requests, and keeps being
 * proven as routes are added.
 * <pre>{@code
 * RestlessOwnershipContract.of(mockMvc, "/notebooks/{notebookId}/notes")
 *         .pathVariable("notebookId", alicesNotebook)
 *         .owner(user("alice")).stranger(user("bob"))
 *         .createBody("{\"text\":\"secret\"}")
 *         .updateBody("{\"text\":\"changed\"}")
 *         .denialStatus(HttpStatus.NOT_FOUND)
 *         .verify();
 * }</pre>
 * {@link #verify()} has the owner create a row, then asserts the stranger:
 * <ul>
 *   <li>is denied {@code GET}/{@code PUT}/{@code DELETE} on it and {@code POST .../bulk-delete} of it;</li>
 *   <li>can't see it in {@code GET .../list} (a denial, or a list without its id);</li>
 *   <li>on a nested resource, is denied a create under the owner's parent and nothing was written;
 *       on a top-level one, creates their own row without it appearing in the owner's collection;</li>
 * </ul>
 * and that the row is still there for its owner. Throws {@link AssertionError} naming the route.
 * Must run outside a test-wide transaction for the "nothing was written" step to mean anything.
 */
public final class RestlessOwnershipContract {

    private final MockMvc mockMvc;
    private final String basePathTemplate;
    private final Map<String, Object> pathVariables = new LinkedHashMap<>();
    private RequestPostProcessor owner;
    private RequestPostProcessor stranger;
    private String createBody;
    private String updateBody;
    private String idPath = "$.id";
    private HttpStatus denialStatus = HttpStatus.NOT_FOUND;
    private boolean bulkDelete = true;

    private RestlessOwnershipContract(MockMvc mockMvc, String basePathTemplate) {
        this.mockMvc = mockMvc;
        this.basePathTemplate = basePathTemplate;
    }

    public static RestlessOwnershipContract of(MockMvc mockMvc, String basePathTemplate) {
        return new RestlessOwnershipContract(mockMvc, basePathTemplate);
    }

    /** A value for a {@code {variable}} of a nested resource's base path - the owner's parent. */
    public RestlessOwnershipContract pathVariable(String name, Object value) {
        pathVariables.put(name, value);
        return this;
    }

    public RestlessOwnershipContract owner(RequestPostProcessor owner) {
        this.owner = owner;
        return this;
    }

    public RestlessOwnershipContract stranger(RequestPostProcessor stranger) {
        this.stranger = stranger;
        return this;
    }

    /** JSON body that creates a row. Required. */
    public RestlessOwnershipContract createBody(String json) {
        this.createBody = json;
        return this;
    }

    /** JSON body for {@code PUT}; skipped when the resource has no update route. */
    public RestlessOwnershipContract updateBody(String json) {
        this.updateBody = json;
        return this;
    }

    /** Where the new row's id is in the create response - {@code $.id} by default. */
    public RestlessOwnershipContract idPath(String jsonPath) {
        this.idPath = jsonPath;
        return this;
    }

    /** What a denial looks like - {@code 404} by default ({@code denyAs = NOT_FOUND}); {@code 403} otherwise. */
    public RestlessOwnershipContract denialStatus(HttpStatus status) {
        this.denialStatus = status;
        return this;
    }

    /** For a resource without {@code DELETE_ALL}. */
    public RestlessOwnershipContract withoutBulkDelete() {
        this.bulkDelete = false;
        return this;
    }

    public void verify() throws Exception {
        if (owner == null || stranger == null || createBody == null) {
            throw new IllegalStateException("owner, stranger and createBody are required");
        }
        String base = UriComponentsBuilder.fromPath(basePathTemplate).buildAndExpand(pathVariables).toUriString();

        MvcResult created = mockMvc.perform(json(post(base), createBody).with(owner)).andReturn();
        expectSuccess(created, "owner POST " + base);
        String id = String.valueOf((Object) JsonPath.read(created.getResponse().getContentAsString(), idPath));
        String item = base + "/" + id;

        expectDenied(get(item).with(stranger), "stranger GET " + item);
        if (updateBody != null) {
            expectDenied(json(put(item), updateBody).with(stranger), "stranger PUT " + item);
        }
        expectDenied(delete(item).with(stranger), "stranger DELETE " + item);
        if (bulkDelete) {
            expectDenied(json(post(base + "/bulk-delete"), "{\"ids\":[\"" + id + "\"]}").with(stranger),
                    "stranger POST " + base + "/bulk-delete");
        }

        MvcResult strangerList = mockMvc.perform(get(base + "/list").with(stranger)).andReturn();
        int listStatus = strangerList.getResponse().getStatus();
        if (listStatus == 200) {
            List<Object> ids = JsonPath.read(strangerList.getResponse().getContentAsString(), "$[*]" + idPath.substring(1));
            if (ids.stream().map(String::valueOf).anyMatch(id::equals)) {
                throw new AssertionError("stranger GET " + base + "/list contains the owner's row " + id);
            }
        } else if (listStatus != denialStatus.value()) {
            throw new AssertionError("stranger GET " + base + "/list: expected 200 without the row, or "
                    + denialStatus.value() + ", got " + listStatus);
        }

        // Nested: creating under the owner's parent is an intrusion and must be denied. Top level:
        // the stranger creating a row of their own is legitimate - it just mustn't land in the
        // owner's collection.
        int before = ownerListSize(base);
        if (pathVariables.isEmpty()) {
            MvcResult own = mockMvc.perform(json(post(base), createBody).with(stranger)).andReturn();
            expectSuccess(own, "stranger POST " + base + " (their own row)");
        } else {
            expectDenied(json(post(base), createBody).with(stranger), "stranger POST " + base);
        }
        int after = ownerListSize(base);
        if (after != before) {
            throw new AssertionError("stranger POST " + base + " changed the owner's collection (" + before + " -> " + after + ")");
        }

        MvcResult stillThere = mockMvc.perform(get(item).with(owner)).andReturn();
        if (stillThere.getResponse().getStatus() != 200) {
            throw new AssertionError("owner GET " + item + " after the stranger's attempts: " + stillThere.getResponse().getStatus());
        }
    }

    private int ownerListSize(String base) throws Exception {
        MvcResult list = mockMvc.perform(get(base + "/list").with(owner)).andReturn();
        if (list.getResponse().getStatus() != 200) {
            throw new AssertionError("owner GET " + base + "/list: " + list.getResponse().getStatus());
        }
        List<Object> rows = JsonPath.read(list.getResponse().getContentAsString(), "$");
        return rows.size();
    }

    private void expectDenied(MockHttpServletRequestBuilder request, String description) throws Exception {
        int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
        if (status != denialStatus.value()) {
            throw new AssertionError(description + ": expected " + denialStatus.value() + ", got " + status);
        }
    }

    private static void expectSuccess(MvcResult result, String description) {
        int status = result.getResponse().getStatus();
        if (status / 100 != 2) {
            throw new AssertionError(description + ": expected 2xx, got " + status);
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
