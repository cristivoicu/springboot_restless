package ro.cristivoicu.springbootrestless.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

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
     * Post-image check for {@code UPDATE}/{@code PATCH}: {@code RestlessResourceHandler} calls
     * this <em>after</em> the write, on the now-mutated entity, in addition to the existing
     * pre-image {@link #canAccess} check it already runs before the write. Catches a transition
     * the pre-image check alone can't see at all - a client successfully passing the pre-check on
     * their own row, then changing an owner/scoping field to something the guard would never have
     * let them touch directly (e.g. reassigning a resource to someone else's account).
     * <p>
     * Defaults to delegating to {@link #canAccess}, so every existing guard implementation keeps
     * behaving exactly as it already did - this is purely additive. <b>Default-on is a real
     * behavior change worth knowing about</b>: a guard whose {@code canAccess} inspects mutable
     * state that a legitimate update is expected to change (e.g. {@code status == DRAFT} as part
     * of an allowed draft→published transition) will now also run that same check against the
     * *new* status and could unexpectedly deny a transition it used to allow. See the Changelog's
     * "Added" entry for this method - whether this should default on at all vs. being strictly
     * opt-in is flagged there as worth revisiting before 1.0.
     * <p>
     * Deliberately no "before" parameter: within the one transaction {@code
     * RestlessResourceHandler} now runs the whole write in, {@code after} the same managed JPA
     * instance {@code canAccess}'s own pre-image check already saw as {@code entity} - the before
     * state is already available by capturing it before the write if a transition rule actually
     * needs to compare the two, same scope {@code WriteAction}-based illegal-transition checks
     * already cover without needing this hook to grow a second entity parameter.
     */
    default boolean canAccessAfterWrite(Action action, String customActionName, HttpServletRequest request, E after) {
        return canAccess(action, customActionName, request, after);
    }

    /**
     * Batched pre-image check for a bulk write (Ground rules Phase 2 item 10) -
     * {@code updateBulk}/{@code deleteAll} call this once against every fetched target instead
     * of looping {@link #canAccess} themselves. Default loops {@link #canAccess} one row at a
     * time (today's existing behavior, unchanged for every guard that doesn't override this), so
     * every existing implementation keeps working identically; override only to batch the
     * underlying check against a real policy engine (see {@code CerbosAuthorizationGuard}, one
     * {@code batch()} RPC instead of N individual ones).
     */
    default boolean canAccessAll(Action action, HttpServletRequest request, List<E> entities) {
        for (E entity : entities) {
            if (!canAccess(action, request, entity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Response-shaping hook (Ground rules Phase 2 item 12) - {@code RestlessResourceHandler} calls
     * this right after {@code Mapper.map(...)} on every single-entity response ({@code create}/
     * {@code findOne}/{@code namedView}/{@code update}/{@code patch}), passing the loaded/written
     * {@code entity} alongside the DTO it just produced. Default returns {@code dto} unchanged -
     * row-level access ({@link #canAccess}/{@link #scope}) is an all-or-nothing decision about
     * whether a principal may see a row at all; this is the seam for the narrower case of a
     * principal who may see the row but not every field on it. {@code <D>} is independent of this
     * interface's own {@code <E>} since a guard's DTO type is whatever each action's own {@code
     * Mapper} happens to produce, not fixed per guard. See {@code CerbosAuthorizationGuard}, which
     * overrides this to automatically mask {@code @CerbosHiddenField}-annotated fields via a
     * Cerbos policy {@code output} - a hand-written {@code Mapper} that already calls {@code
     * CerbosFieldMasker} itself keeps working unaffected, masking is idempotent.
     */
    default <D> D postProcessResponse(Action action, String customActionName, HttpServletRequest request, E entity, D dto) {
        return dto;
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
