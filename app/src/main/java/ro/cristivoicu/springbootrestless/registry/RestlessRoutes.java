package ro.cristivoicu.springbootrestless.registry;

import org.springframework.web.bind.annotation.RequestMethod;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.util.List;

/**
 * The canonical fixed-route table - one entry per route {@link RestlessResourceHandler} exposes,
 * mirroring the nine endpoints the four Stage-0 {@code *Controller} classes used to provide by
 * hand, plus bulk create/update. Extracted out of {@link RestlessRegistrar} (the only consumer
 * until now) so a second consumer - {@code ro.cristivoicu.springbootrestless.openapi}'s default
 * OpenAPI document generation - can describe exactly the same routes {@code RestlessRegistrar}
 * actually registers, from one shared source of truth, instead of a second hand-copied table
 * silently drifting out of sync with it the next time a route is added or changed here.
 */
public final class RestlessRoutes {

    /**
     * {@code operation}: which {@link RestlessResourceHandler#getEnabledOperations} entry gates
     * this route - shared by more than one {@code RouteDefinition} where a bulk route rides along
     * with its single-item counterpart ({@code createBulk} with {@code CREATE}, {@code
     * updateBulk} with {@code UPDATE}), so disabling one disables both together.
     */
    public record RouteDefinition(String handlerMethodName, String pathSuffix, RequestMethod httpMethod,
                                   AuthorizationGuard.Action operation) {
    }

    // (createAll()/updateAll() default methods on CreateDataSource/UpdateDataSource - every
    // resource already has one of each, so unlike PATCH these aren't opt-in, only individually
    // disable-able via getEnabledOperations()).
    public static final List<RouteDefinition> FIXED = List.of(
            new RouteDefinition("create", "", RequestMethod.POST, AuthorizationGuard.Action.CREATE),
            new RouteDefinition("createBulk", "/bulk", RequestMethod.POST, AuthorizationGuard.Action.CREATE),
            new RouteDefinition("findOne", "/{id}", RequestMethod.GET, AuthorizationGuard.Action.READ_ONE),
            new RouteDefinition("findList", "/list", RequestMethod.GET, AuthorizationGuard.Action.READ_LIST),
            new RouteDefinition("findPage", "", RequestMethod.GET, AuthorizationGuard.Action.READ_PAGE),
            new RouteDefinition("findPageOverview", "/overview", RequestMethod.GET, AuthorizationGuard.Action.READ_PAGE_OVERVIEW),
            new RouteDefinition("findPageSelect", "/select/async", RequestMethod.GET, AuthorizationGuard.Action.READ_PAGE_SELECT),
            new RouteDefinition("update", "/{id}", RequestMethod.PUT, AuthorizationGuard.Action.UPDATE),
            new RouteDefinition("updateBulk", "/bulk", RequestMethod.PUT, AuthorizationGuard.Action.UPDATE),
            new RouteDefinition("deleteById", "/{id}", RequestMethod.DELETE, AuthorizationGuard.Action.DELETE_ONE),
            // POST, not DELETE-with-a-body: RFC 9110 gives a DELETE request body no defined
            // semantics, and in practice proxies/CDNs/browser fetch() are known to drop it - a
            // bulk operation that depends on its body arriving is exactly the wrong place for
            // that. "/bulk-delete" (not just basePath, unlike deleteById's DELETE {id}) so it
            // doesn't collide with POST {basePath} (create) / POST {basePath}/bulk (createBulk).
            new RouteDefinition("deleteAll", "/bulk-delete", RequestMethod.POST, AuthorizationGuard.Action.DELETE_ALL)
    );

    private RestlessRoutes() {
    }
}
