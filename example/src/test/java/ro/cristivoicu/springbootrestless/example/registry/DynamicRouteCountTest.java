package ro.cristivoicu.springbootrestless.example.registry;

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
 * {@code RestlessRegistrar} must have registered exactly eleven fixed routes (see {@code
 * RestlessRegistrar.ROUTES} - the original nine plus bulk create/update) for each of the three
 * full-CRUD {@code @RestlessResource} beans currently in the app (Employee, Department, Project -
 * all three hand-wired manual resources, each with its own {@code CerbosAuthorizationGuard}),
 * plus {@code ProjectAssignment}'s nine (every fixed route except {@code update}/{@code
 * updateBulk} - see {@code ProjectAssignmentRestlessResource#getEnabledOperations}), plus one
 * extra route per named custom read action (Employee's {@code byEmailDomain}), plus one extra
 * route per named write action (Employee's {@code promote}/{@code giveRaise}/{@code
 * addCertification}/{@code recordAchievement}), plus one extra route per named view (Employee's
 * {@code contact}), plus one extra opt-in {@code PATCH} route (Department's), no more, no fewer.
 */
@SpringBootTest
class DynamicRouteCountTest {

    private static final int FIXED_ROUTES_PER_RESOURCE = 11;
    private static final int FULL_CRUD_RESOURCE_COUNT = 3; // Employee, Department, Project
    private static final int NO_UPDATE_ROUTE_COUNT = 9; // ProjectAssignment: everything but update/updateBulk
    private static final int CUSTOM_READ_ACTION_COUNT = 1; // Employee's "byEmailDomain"
    private static final int CUSTOM_WRITE_ACTION_COUNT = 4; // Employee's "promote"/"giveRaise"/"addCertification"/"recordAchievement"
    private static final int NAMED_VIEW_COUNT = 1; // Employee's "contact"
    private static final int PATCH_ROUTE_COUNT = 1; // Department's

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
                (long) (FIXED_ROUTES_PER_RESOURCE * FULL_CRUD_RESOURCE_COUNT) + NO_UPDATE_ROUTE_COUNT
                        + CUSTOM_READ_ACTION_COUNT + CUSTOM_WRITE_ACTION_COUNT + NAMED_VIEW_COUNT + PATCH_ROUTE_COUNT);
    }
}
