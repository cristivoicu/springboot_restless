package ro.cristivoicu.springbootrestless.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A context-load-level check that catches silent registration failures - {@code
 * RestlessRegistrar} must have registered exactly eleven fixed routes (see {@code
 * RestlessRegistrar.ROUTES} - the original nine plus bulk create/update) for each of the four
 * full-CRUD {@code @RestlessResource} beans currently in the app (Gadget, Gizmo, the
 * compile-time-generated Sprocket and Doohickey - the latter also exercising the generated
 * default Mapper, an annotation-wired {@code AuthorizationGuard}, and an opt-in {@code PATCH}
 * route), plus Cog's three read-only routes ({@code @RestlessEntity(operations = ...)} - see
 * {@code CogGeneratedResourceTest}), plus Bolt's three ({@code CREATE} - {@code create}/{@code
 * createBulk} - plus {@code READ_LIST}; see {@code BulkTransactionRollbackTest}), plus one extra
 * route per named custom read action (Gadget's {@code byEmailDomain}) and per opt-in {@code
 * PATCH} route (Doohickey's), no more, no fewer.
 */
@SpringBootTest
class DynamicRouteCountTest {

    private static final int FIXED_ROUTES_PER_RESOURCE = 11;
    private static final int FULL_CRUD_RESOURCE_COUNT = 4; // Gadget, Gizmo, Sprocket, Doohickey (both generated)
    private static final int READ_ONLY_ROUTE_COUNT = 3; // Cog: READ_ONE, READ_LIST, READ_PAGE only
    private static final int CREATE_AND_LIST_ROUTE_COUNT = 3; // Bolt: create, createBulk, findList only
    private static final int CUSTOM_READ_ACTION_COUNT = 1; // Gadget's "byEmailDomain"
    private static final int OPT_IN_PATCH_ROUTE_COUNT = 1; // Doohickey's

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void registersExactlyTheExpectedNumberOfDynamicRoutes() {
        Map<RequestMappingInfo, org.springframework.web.method.HandlerMethod> handlerMethods =
                requestMappingHandlerMapping.getHandlerMethods();

        long dynamicRouteCount = handlerMethods.values().stream()
                .filter(handlerMethod -> handlerMethod.getBean() instanceof RestlessResourceHandler)
                .count();

        assertThat(dynamicRouteCount).isEqualTo(
                (long) (FIXED_ROUTES_PER_RESOURCE * FULL_CRUD_RESOURCE_COUNT) + READ_ONLY_ROUTE_COUNT
                        + CREATE_AND_LIST_ROUTE_COUNT + CUSTOM_READ_ACTION_COUNT + OPT_IN_PATCH_ROUTE_COUNT);
    }
}
