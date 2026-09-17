package ro.cristivoicu.springbootrestless.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live proof (against the real generated {@code /v3/api-docs} document, not a unit test of
 * {@link RestlessOpenApiCustomizer} in isolation) of the two formerly-known simplifications this
 * class's own javadoc now says are addressed: a versioned resource's operations carry {@code
 * x-api-version}, and each page-read variant resolves its own item schema type (even though every
 * real entity in this reactor happens to use the same mapper for all three today, so the *schema*
 * itself can't visibly differ yet - only {@link
 * ro.cristivoicu.springbootrestless.resource.ResourceMetadata}'s three now-distinct fields can be
 * checked directly).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestlessOpenApiCustomizerVersionAndProjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void versionedResourceOperationsCarryTheVersionExtension() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode docs = objectMapper.readTree(body);

        // Doohickey declares version = "1" (see DynamicRouteVersionTest) - its findPage
        // ("GET /doohickeys") operation should carry the vendor extension and summary suffix.
        // Swagger-core serializes a vendor extension as a top-level "x-*" sibling of the
        // operation's other fields, not nested under a literal "extensions" key.
        JsonNode getOperation = docs.get("paths").get("/doohickeys").get("get");
        assertThat(getOperation.get("x-api-version").asString()).isEqualTo("1");
        assertThat(getOperation.get("summary").asString()).contains("(API version: 1)");

        // An unversioned dynamic resource (Gadget's "/gadgets-dynamic" - "/gadgets" itself is a
        // hand-written parity controller springdoc's own scan documents, not this customizer's
        // doing at all) carries no such extension.
        JsonNode gadgetGet = docs.get("paths").get("/gadgets-dynamic").get("get");
        assertThat(gadgetGet.has("x-api-version")).isFalse();
    }

    @Test
    void everyPageReadVariantResolvesItsOwnResponseSchema() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode docs = objectMapper.readTree(body);

        String pageSchema = itemSchemaRef(docs, "/gadgets-dynamic", "get");
        String overviewSchema = itemSchemaRef(docs, "/gadgets-dynamic/overview", "get");
        String selectSchema = itemSchemaRef(docs, "/gadgets-dynamic/select/async", "get");

        // Gadget uses the same mapper for all three today (see EmployeeReadController's own
        // equivalent pattern), so the resolved $ref is the same value across all three - the
        // point of this assertion is that each was independently resolved via ResourceMetadata's
        // now-separate overviewResponseDtoType()/selectResponseDtoType() fields (see
        // RestlessResourceHandler#resolveMetadata) and produced a real schema $ref, not null.
        assertThat(pageSchema).isNotBlank();
        assertThat(overviewSchema).isEqualTo(pageSchema);
        assertThat(selectSchema).isEqualTo(pageSchema);
    }

    private String itemSchemaRef(JsonNode docs, String path, String httpMethod) {
        return docs.get("paths").get(path).get(httpMethod)
                .get("responses").get("200").get("content").get("application/json")
                .get("schema").get("properties").get("body").get("items").get("$ref").asString();
    }
}
