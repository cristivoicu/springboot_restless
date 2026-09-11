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
 * RestlessRegistrar} must have registered exactly nine fixed routes (see
 * {@code RestlessRegistrar.ROUTES}) for each of the three {@code @RestlessResource} beans
 * currently in the app (Gadget, Gizmo, the compile-time-generated Sprocket), plus one extra
 * route per named custom read action (Gadget's {@code byEmailDomain}), no more, no fewer.
 */
@SpringBootTest
class DynamicRouteCountTest {

    private static final int FIXED_ROUTES_PER_RESOURCE = 9;
    private static final int RESOURCE_COUNT = 3; // Gadget, Gizmo, Sprocket (generated)
    private static final int CUSTOM_READ_ACTION_COUNT = 1; // Gadget's "byEmailDomain"

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void registersExactlyTheExpectedNumberOfDynamicRoutes() {
        Map<RequestMappingInfo, org.springframework.web.method.HandlerMethod> handlerMethods =
                requestMappingHandlerMapping.getHandlerMethods();

        long dynamicRouteCount = handlerMethods.values().stream()
                .filter(handlerMethod -> handlerMethod.getBean() instanceof RestlessResourceHandler)
                .count();

        assertThat(dynamicRouteCount).isEqualTo((long) (FIXED_ROUTES_PER_RESOURCE * RESOURCE_COUNT) + CUSTOM_READ_ACTION_COUNT);
    }
}
