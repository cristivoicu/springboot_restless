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
 * Stage 4: a context-load-level check that catches silent registration failures -
 * {@code RestlessRegistrar} must have registered exactly nine routes (see
 * {@code RestlessRegistrar.ROUTES}) for each of the two {@code @RestlessResource} beans
 * currently in the app (Employee, Department), no more, no fewer.
 */
@SpringBootTest
class DynamicRouteCountTest {

    private static final int ROUTES_PER_RESOURCE = 9;
    private static final int RESOURCE_COUNT = 2; // EmployeeRestlessResource, DepartmentRestlessResource

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void registersExactlyTheExpectedNumberOfDynamicRoutes() {
        Map<RequestMappingInfo, org.springframework.web.method.HandlerMethod> handlerMethods =
                requestMappingHandlerMapping.getHandlerMethods();

        long dynamicRouteCount = handlerMethods.values().stream()
                .filter(handlerMethod -> handlerMethod.getBean() instanceof RestlessResourceHandler)
                .count();

        assertThat(dynamicRouteCount).isEqualTo((long) ROUTES_PER_RESOURCE * RESOURCE_COUNT);
    }
}
