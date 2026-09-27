package ro.cristivoicu.springbootrestless.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * Framework-native, pluggable per-action authorization hook — deliberately no Spring Security
 * dependency, since the app has none today and the guard should be wireable to whatever auth
 * stack shows up later. Threads {@link HttpServletRequest} itself rather than an abstracted
 * "principal" type: implementations read {@code request.getUserPrincipal()}, a header, or
 * whatever attribute their own auth filter already populates.
 * <p>
 * Wired via {@code RestlessResourceHandler.getAuthorizationGuard()} (default-permissive, so
 * authorization is opt-in per resource, not mandatory boilerplate). Three hook points, checked
 * at different points in each action's control flow:
 * <ul>
 *     <li>{@link #preCheck} — coarse, before any work: "can this principal even attempt this
 *     action at all". Denial short-circuits with 403 before touching the database.</li>
 *     <li>{@link #scope} — row-level restriction for read actions: an extra {@link Specification}
 *     ANDed onto whatever filter the action would otherwise use, so list/page results are
 *     naturally scoped down to only the rows this principal may see. {@code null} means
 *     unrestricted.</li>
 *     <li>{@link #canAccess} — per-instance check once a specific entity has been loaded (single
 *     read, update, delete): "can this principal act on THIS row specifically".</li>
 * </ul>
 *
 * @param <E> the entity type this guard protects
 */
public interface AuthorizationGuard<E> {

    enum Action {
        CREATE, READ_ONE, READ_LIST, READ_PAGE, READ_PAGE_OVERVIEW, READ_PAGE_SELECT,
        UPDATE, PATCH, DELETE_ONE, DELETE_ALL, CUSTOM_READ, NAMED_VIEW, WRITE_ACTION
    }

    default boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        return true;
    }

    default Specification<E> scope(Action action, String customActionName, HttpServletRequest request) {
        return null;
    }

    default boolean canAccess(Action action, HttpServletRequest request, E entity) {
        return true;
    }

    /**
     * Same as {@link #canAccess(Action, HttpServletRequest, Object)}, plus a name - the {@code
     * canAccess} counterpart {@link #preCheck} already had via its own {@code customActionName}
     * parameter, {@code canAccess} never did, since every action that reaches it (single read,
     * update, patch, delete) was always exactly one per {@link Action}. {@link Action#NAMED_VIEW}
     * (see {@code RestlessResourceHandler#getNamedViews}) is the first that isn't - several named
     * views can share one {@code Action}, distinguished only by name, the same way {@link
     * Action#CUSTOM_READ} already needed {@code customActionName} for {@link #preCheck}. Default
     * delegates to the three-arg overload, ignoring the name - every existing implementation that
     * only overrides that one keeps behaving identically here too; override this one instead of
     * (or in addition to) the three-arg version only when a guard needs to differentiate by name.
     */
    default boolean canAccess(Action action, String customActionName, HttpServletRequest request, E entity) {
        return canAccess(action, request, entity);
    }

    /**
     * The shared no-op instance {@code getAuthorizationGuard()} defaults to. A singleton (not a
     * fresh instance per call) so {@code RestlessResourceHandler} can reference-compare against
     * it to detect "no guard configured" and skip the extra per-instance load that {@code
     * canAccess} would otherwise require on every update/delete — pure overhead when nothing is
     * actually going to deny anything.
     */
    AuthorizationGuard<?> ALLOW_ALL = new AuthorizationGuard<>() {
    };

    @SuppressWarnings("unchecked")
    static <E> AuthorizationGuard<E> allowAll() {
        return (AuthorizationGuard<E>) ALLOW_ALL;
    }
}
