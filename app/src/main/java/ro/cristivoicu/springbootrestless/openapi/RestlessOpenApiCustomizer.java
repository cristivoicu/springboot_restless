package ro.cristivoicu.springbootrestless.openapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.PrimitiveType;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.registry.RestlessRoutes;
import ro.cristivoicu.springbootrestless.resource.ReadAction;
import ro.cristivoicu.springbootrestless.resource.ResourceMetadata;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Describes every route {@code RestlessRegistrar} actually registered - the fixed CRUD/bulk
 * routes, named custom read actions, the opt-in {@code PATCH} route - so they show up in
 * {@code /v3/api-docs}/Swagger UI at all. Necessary because springdoc's own {@code
 * @RestController} scanning genuinely doesn't see them: it discovers routes by finding beans
 * annotated {@code @Controller}/{@code @RestController}, but {@code RestlessRegistrar} registers
 * {@code (RequestMappingInfo, resourceBean, handlerMethod)} directly against {@code
 * RequestMappingHandlerMapping}, bypassing that discovery entirely (confirmed empirically, not
 * assumed - see {@code OpenApiDiscoverySpikeTest} in the {@code example} module, which found
 * exactly this before this class existed to fix it).
 * <p>
 * A {@link GlobalOpenApiCustomizer} (not {@code OpenApiCustomizer} directly, though it extends
 * it) - guaranteed to run regardless of whether the consuming app configures multiple springdoc
 * "groups", since it runs once globally rather than per group. springdoc calls {@link #customise}
 * <em>after</em> building the base document from its own scan, so this only ever <em>adds</em>
 * paths/schemas the base scan didn't already find - see {@link #putOperation} for the
 * don't-clobber-an-existing-entry guard.
 * <p>
 * {@code @ConditionalOnClass}, not a hard dependency: {@code app} compiles against springdoc as
 * an {@code optional} Maven dependency (see its {@code pom.xml}) specifically so this class can
 * exist, but a consumer with no springdoc on their own classpath gets no bean here at all, not a
 * {@code ClassNotFoundException} - see {@code app}'s {@code pom.xml} for the full reasoning.
 * <p>
 * <b>Known simplifications, not oversights:</b> {@code findPage}/{@code findPageOverview}/{@code
 * findPageSelect} all document {@link ResourceMetadata#responseDtoType()} as their item schema,
 * even though overview/select projections can genuinely differ in a hand-written resource - the
 * mapper each one is actually built from isn't separately resolved anywhere (see {@code
 * RestlessResourceHandler#resolveMetadata}'s own javadoc, which only resolves {@code
 * getEntityMapper()}'s type). API-versioned routes ({@code @RestlessResource(version = ...)})
 * document their path with no version distinction at all - OpenAPI has no native way to express
 * Spring's header-based version resolution strategy without a custom vendor extension, not
 * attempted here. Both are real, currently-permanent gaps, not bugs to report.
 */
@Component
@ConditionalOnClass(GlobalOpenApiCustomizer.class)
public class RestlessOpenApiCustomizer implements GlobalOpenApiCustomizer {

    private final ApplicationContext applicationContext;

    public RestlessOpenApiCustomizer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        if (openApi.getComponents().getSchemas() == null) {
            openApi.getComponents().setSchemas(new LinkedHashMap<>());
        }
        if (openApi.getPaths() == null) {
            openApi.setPaths(new Paths());
        }

        applicationContext.getBeansOfType(RestlessResourceHandler.class).values()
                .forEach(resource -> describe(resource, openApi));
    }

    private void describe(RestlessResourceHandler<?, ?> resource, OpenAPI openApi) {
        ResourceMetadata metadata = resource.getMetadata();
        if (metadata == null) {
            return; // Not yet wired by RestlessRegistrar - shouldn't happen once the context is up.
        }
        String tag = metadata.entityType().getSimpleName();
        Set<AuthorizationGuard.Action> enabled = resource.getEnabledOperations();

        for (RestlessRoutes.RouteDefinition route : RestlessRoutes.FIXED) {
            if (enabled.contains(route.operation())) {
                addFixedRoute(openApi, metadata, tag, route);
            }
        }

        resource.getCustomReadActions().forEach((actionName, action) ->
                addCustomReadRoute(openApi, metadata, tag, actionName, action));

        resource.getPatchDataSource().ifPresent(ignored -> addPatchRoute(openApi, metadata, tag, resource));
    }

    // ---- one branch per fixed-route shape ----

    private void addFixedRoute(OpenAPI openApi, ResourceMetadata metadata, String tag, RestlessRoutes.RouteDefinition route) {
        String path = metadata.basePath() + route.pathSuffix();
        Operation operation = new Operation()
                .addTagsItem(tag)
                .operationId(operationId(route.handlerMethodName(), metadata))
                .summary(summary(route.handlerMethodName(), tag));

        switch (route.handlerMethodName()) {
            case "create" -> {
                operation.requestBody(jsonBody(refSchema(openApi, metadata.createModelType()), true));
                operation.responses(new ApiResponses().addApiResponse("200",
                        jsonResponse(refSchema(openApi, metadata.responseDtoType()))));
            }
            case "createBulk" -> {
                operation.requestBody(jsonBody(arraySchema(refSchema(openApi, metadata.createModelType())), true));
                operation.responses(new ApiResponses().addApiResponse("200",
                        jsonResponse(arraySchema(refSchema(openApi, metadata.responseDtoType())))));
            }
            case "findOne" -> {
                operation.addParametersItem(idParameter(openApi, metadata.idType()));
                operation.responses(new ApiResponses()
                        .addApiResponse("200", jsonResponse(refSchema(openApi, metadata.responseDtoType())))
                        .addApiResponse("404", new ApiResponse().description("Not found")));
            }
            case "findList" -> {
                addSearchParameters(operation, openApi, metadata.searchDtoType());
                operation.responses(new ApiResponses().addApiResponse("200",
                        jsonResponse(arraySchema(refSchema(openApi, metadata.responseDtoType())))));
            }
            case "findPage", "findPageOverview", "findPageSelect" -> {
                addSearchParameters(operation, openApi, metadata.searchDtoType());
                addPagingParameters(operation, openApi);
                operation.responses(new ApiResponses().addApiResponse("200",
                        jsonResponse(pageSchema(refSchema(openApi, metadata.responseDtoType())))));
            }
            case "update" -> {
                operation.addParametersItem(idParameter(openApi, metadata.idType()));
                operation.requestBody(jsonBody(refSchema(openApi, metadata.updateModelType()), true));
                operation.responses(new ApiResponses().addApiResponse("200",
                        jsonResponse(refSchema(openApi, metadata.responseDtoType()))));
            }
            case "updateBulk" -> {
                operation.requestBody(jsonBody(new MapSchema().additionalProperties(refSchema(openApi, metadata.updateModelType())), true));
                operation.responses(new ApiResponses().addApiResponse("200",
                        jsonResponse(arraySchema(refSchema(openApi, metadata.responseDtoType())))));
            }
            case "deleteById" -> {
                operation.addParametersItem(idParameter(openApi, metadata.idType()));
                operation.responses(noContentResponses());
            }
            case "deleteAll" -> {
                operation.requestBody(jsonBody(refSchema(openApi, metadata.deleteModelType()), true));
                operation.responses(noContentResponses());
            }
            default -> throw new IllegalStateException("RestlessRoutes lists an unknown handler method: " + route.handlerMethodName());
        }

        putOperation(openApi, path, route.httpMethod(), operation);
    }

    private void addCustomReadRoute(OpenAPI openApi, ResourceMetadata metadata, String tag, String actionName, ReadAction<?, ?> action) {
        String path = metadata.basePath() + "/actions/" + actionName;
        Operation operation = new Operation()
                .addTagsItem(tag)
                .operationId(operationId("action_" + actionName, metadata))
                .summary("Custom read action \"" + actionName + "\" on " + tag);
        addSearchParameters(operation, openApi, action.getSearchDtoType());
        addPagingParameters(operation, openApi);
        operation.responses(new ApiResponses().addApiResponse("200",
                jsonResponse(pageSchema(refSchema(openApi, metadata.responseDtoType())))));
        putOperation(openApi, path, RequestMethod.GET, operation);
    }

    private void addPatchRoute(OpenAPI openApi, ResourceMetadata metadata, String tag, RestlessResourceHandler<?, ?> resource) {
        String path = metadata.basePath() + "/{id}";
        Operation operation = new Operation()
                .addTagsItem(tag)
                .operationId(operationId("patch", metadata))
                .summary("Partially update " + tag + " by id");
        operation.addParametersItem(idParameter(openApi, metadata.idType()));
        operation.requestBody(jsonBody(refSchema(openApi, resource.getPatchModelType()), true));
        operation.responses(new ApiResponses().addApiResponse("200",
                jsonResponse(refSchema(openApi, metadata.responseDtoType()))));
        putOperation(openApi, path, RequestMethod.PATCH, operation);
    }

    // ---- shared building blocks ----

    /**
     * Adds {@code operation} to {@code path}'s {@link PathItem} for {@code httpMethod}, creating
     * the {@link PathItem} if this is the first route documented at that path - but only if
     * nothing's there yet for that method. The guard matters for one real case: springdoc's own
     * scan may already have found a hand-written {@code @RestController} at the very same literal
     * path (the parity-testing pattern several of this reactor's own fixtures use, e.g. {@code
     * /employees} hand-written next to {@code /employees-dynamic} generated) - this must never
     * overwrite what springdoc already documented correctly for that case.
     */
    private void putOperation(OpenAPI openApi, String path, RequestMethod httpMethod, Operation operation) {
        PathItem pathItem = openApi.getPaths().computeIfAbsent(path, ignored -> new PathItem());
        switch (httpMethod) {
            case GET -> { if (pathItem.getGet() == null) pathItem.setGet(operation); }
            case POST -> { if (pathItem.getPost() == null) pathItem.setPost(operation); }
            case PUT -> { if (pathItem.getPut() == null) pathItem.setPut(operation); }
            case PATCH -> { if (pathItem.getPatch() == null) pathItem.setPatch(operation); }
            case DELETE -> { if (pathItem.getDelete() == null) pathItem.setDelete(operation); }
            default -> throw new IllegalStateException("No Restless route ever uses " + httpMethod);
        }
    }

    private RequestBody jsonBody(Schema<?> schema, boolean required) {
        return new RequestBody().required(required)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private ApiResponse jsonResponse(Schema<?> schema) {
        return new ApiResponse().description("OK")
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private ApiResponses noContentResponses() {
        return new ApiResponses().addApiResponse("204", new ApiResponse().description("No Content"));
    }

    private Schema<?> arraySchema(Schema<?> items) {
        return new ArraySchema().items(items);
    }

    /** {@code RestlessResourceHandler#findPage}'s {@code PageableResponse} shape, inlined rather than a shared named component - four fields, not worth the indirection. */
    private Schema<?> pageSchema(Schema<?> itemSchema) {
        return new ObjectSchema()
                .addProperty("pageSize", PrimitiveType.INT.createProperty())
                .addProperty("totalPages", PrimitiveType.INT.createProperty())
                .addProperty("totalElements", PrimitiveType.LONG.createProperty())
                .addProperty("body", arraySchema(itemSchema));
    }

    private Parameter idParameter(OpenAPI openApi, Class<?> idType) {
        return new Parameter().name("id").in("path").required(true).schema(refSchema(openApi, idType));
    }

    /**
     * One query parameter per {@code searchDtoType}'s own declared field - {@code null}-safe (a
     * disabled READ_LIST/READ_PAGE* still routes here with no search DTO to describe... never
     * actually happens, since getReadDataSource() stays mandatory, but defensive regardless).
     * <p>
     * Unlike {@link #refSchema}, this never delegates to {@code ModelConverters} for the field's
     * own type - these are exploded query parameters, not a nested object schema - so a field's
     * {@code @Schema(hidden = true)}, {@code description} and {@code example} are read directly
     * off the field here rather than coming along for free the way they do for a {@code $ref}ed
     * DTO. Keeps parity with the hand-written-controller path (springdoc's own scan resolves the
     * whole search DTO as a single object parameter via the same {@code ModelConverters}
     * machinery {@link #refSchema} uses, so those annotations already apply there) - a field
     * documented for one path shouldn't silently go undocumented on the other.
     */
    private void addSearchParameters(Operation operation, OpenAPI openApi, Class<?> searchDtoType) {
        if (searchDtoType == null) {
            return;
        }
        for (Field field : searchDtoType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            io.swagger.v3.oas.annotations.media.Schema annotation =
                    field.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
            if (annotation != null && annotation.hidden()) {
                continue;
            }
            Schema<?> fieldSchema = refSchema(openApi, field.getType());
            if (annotation != null) {
                if (!annotation.description().isBlank()) {
                    fieldSchema.setDescription(annotation.description());
                }
                if (!annotation.example().isBlank()) {
                    fieldSchema.setExample(annotation.example());
                }
            }
            operation.addParametersItem(new Parameter()
                    .name(field.getName())
                    .in("query")
                    .required(false)
                    .schema(fieldSchema));
        }
    }

    /** {@code AbstractSearchDto}'s inherited page/size/sort, described once by hand rather than via reflection - {@code app} has no compile-time dependency from this package back onto {@code models} needed for that, and the shape is fixed regardless of entity. */
    private void addPagingParameters(Operation operation, OpenAPI openApi) {
        operation.addParametersItem(new Parameter().name("page").in("query").required(false)
                .description("0-based page index (default 0)").schema(refSchema(openApi, Integer.class)));
        operation.addParametersItem(new Parameter().name("size").in("query").required(false)
                .description("Page size (default 20)").schema(refSchema(openApi, Integer.class)));
        operation.addParametersItem(new Parameter().name("sort").in("query").required(false)
                .description("property,ASC|DESC - repeat for multiple sort fields").schema(refSchema(openApi, String.class)));
    }

    /**
     * A {@link Schema} for any {@code type}: a plain primitive/wrapper schema for something
     * {@link PrimitiveType} already knows (id types, search-parameter field types, ...), a {@code
     * $ref} into {@code components.schemas} (registering it there first if this is the first time
     * it's been seen) for a real DTO class, or a bare {@code object} schema for {@code null} -
     * {@link ResourceMetadata#responseDtoType()}'s own documented "best-effort, may be
     * unresolvable" case, not an error here either.
     */
    private Schema<?> refSchema(OpenAPI openApi, Class<?> type) {
        if (type == null) {
            return new Schema<>().type("object");
        }
        PrimitiveType primitive = PrimitiveType.fromType(type);
        if (primitive != null) {
            return primitive.createProperty();
        }
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
        if (resolved == null || resolved.schema == null) {
            return new Schema<>().type("object");
        }
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach((name, schema) -> openApi.getComponents().getSchemas().putIfAbsent(name, schema));
        }
        return resolved.schema;
    }

    private String operationId(String verb, ResourceMetadata metadata) {
        return verb + "_" + metadata.entityType().getSimpleName();
    }

    private String summary(String handlerMethodName, String entityName) {
        return switch (handlerMethodName) {
            case "create" -> "Create " + entityName;
            case "createBulk" -> "Bulk create " + entityName + " (JSON array body)";
            case "findOne" -> "Find one " + entityName + " by id";
            case "findList" -> "List every matching " + entityName + " (unpaged)";
            case "findPage" -> "Page through " + entityName;
            case "findPageOverview" -> "Page through " + entityName + " (overview projection)";
            case "findPageSelect" -> "Page through " + entityName + " (select projection)";
            case "update" -> "Replace " + entityName + " by id";
            case "updateBulk" -> "Bulk replace " + entityName + " (JSON object keyed by id)";
            case "deleteById" -> "Delete " + entityName + " by id";
            case "deleteAll" -> "Bulk delete " + entityName + " by ids";
            default -> handlerMethodName + " " + entityName;
        };
    }
}
