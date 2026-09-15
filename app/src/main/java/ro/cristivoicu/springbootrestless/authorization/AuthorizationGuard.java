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
        UPDATE, PATCH, DELETE_ONE, DELETE_ALL, CUSTOM_READ
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
