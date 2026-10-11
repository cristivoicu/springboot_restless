package ro.cristivoicu.springbootrestless.authorization.ownership;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.resource.RestlessContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * "A user manages only what they own" as a ready-made {@link AuthorizationGuard} - built by
 * {@link RestlessOwnershipGuards}, usually through {@code @RestlessOwnedBy} on the entity.
 * <p>
 * Two shapes:
 * <ul>
 *   <li><b>direct</b> - the row carries its owner: {@code Event.ownerId == principal};</li>
 *   <li><b>via</b> - the row belongs to a parent that carries the owner:
 *       {@code Guest.eventId -> Event.id, Event.ownerId == principal}.</li>
 * </ul>
 * Enforced at every hook: {@link #scope} restricts lists in SQL (a subquery for the via shape),
 * {@link #canAccess} checks single rows - including a freshly created one, still inside its
 * transaction, so a create pointing at someone else's parent is rolled back - and the inherited
 * {@code canAccessAfterWrite}/{@code canAccessAll} apply the same rule. A via-guard on a nested
 * resource whose parent field is its link answers for the path's parent once, in {@link #preCheck},
 * so a foreign parent is denied before anything is read or parsed.
 * <p>
 * Fails closed: no principal ({@link RestlessContext#principalId()} {@code null}), or a principal
 * that doesn't convert to the owner field's type, owns nothing. Ownership of a parent is looked up
 * at most once per parent per request.
 */
public final class OwnershipAuthorizationGuard<E> implements AuthorizationGuard<E> {

    private static final String CACHE_ATTRIBUTE = OwnershipAuthorizationGuard.class.getName() + ".owned";

    private final EntityManager entityManager;
    private final PropertyPath ownerPath;      // direct: on E; via: on the parent type
    private final PropertyPath linkPath;       // via only: on E, pointing at the parent's id
    private final Class<?> parentType;         // via only
    private final String parentIdProperty;     // via only
    private final Class<?> parentIdType;       // via only

    OwnershipAuthorizationGuard(EntityManager entityManager, PropertyPath ownerPath,
                                PropertyPath linkPath, Class<?> parentType) {
        this.entityManager = entityManager;
        this.ownerPath = ownerPath;
        this.linkPath = linkPath;
        this.parentType = parentType;
        this.parentIdProperty = parentType == null ? null : PropertyPath.idPropertyOf(parentType);
        this.parentIdType = parentType == null ? null : PropertyPath.of(parentType, parentIdProperty).leafType();
    }

    private boolean isVia() {
        return parentType != null;
    }

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        RestlessContext context = RestlessContext.current();
        if (context == null || context.principalId() == null) {
            return false;
        }
        // Nested under the very parent this guard links through: answer for the path's parent up
        // front. Every row the handler reaches afterwards is checked to belong to that parent.
        if (isVia() && linkPath.expression().equals(context.parentField())) {
            return ownsParent(context.parentValue(), context);
        }
        return true;
    }

    @Override
    public Specification<E> scope(Action action, String customActionName, HttpServletRequest request) {
        RestlessContext context = RestlessContext.current();
        Object me = context == null ? null : context.principalId(ownerPath.leafType());
        if (me == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        if (!isVia()) {
            return (root, query, cb) -> cb.equal(ownerPath.on(root), me);
        }
        return (root, query, cb) -> {
            // Typed with the parent's real id type: Hibernate refuses to compare a Long/UUID column
            // with an Object-typed subquery.
            @SuppressWarnings({"unchecked", "rawtypes"})
            Subquery<Object> owned = (Subquery) query.subquery(parentIdType);
            Root<?> parent = owned.from(parentType);
            owned.select(parent.get(parentIdProperty)).where(cb.equal(ownerPath.on(parent), me));
            return linkPath.on(root).in(owned);
        };
    }

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, E entity) {
        RestlessContext context = RestlessContext.current();
        if (context == null || entity == null) {
            return false;
        }
        if (!isVia()) {
            Object me = context.principalId(ownerPath.leafType());
            return me != null && Objects.equals(ownerPath.read(entity), me);
        }
        return ownsParent(linkPath.read(entity), context);
    }

    @Override
    public boolean canAccessAll(Action action, HttpServletRequest request, List<E> entities) {
        for (E entity : entities) {
            if (!canAccess(action, request, entity)) {
                return false;
            }
        }
        return true;
    }

    private boolean ownsParent(Object parentId, RestlessContext context) {
        if (parentId == null) {
            return false;
        }
        Object me = context.principalId(ownerPath.leafType());
        if (me == null) {
            return false;
        }
        Map<Object, Boolean> cache = cacheOf(context.request());
        return cache.computeIfAbsent(parentType.getName() + ':' + parentId, key -> countOwned(parentId, me) > 0);
    }

    private long countOwned(Object parentId, Object me) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<?> parent = query.from(parentType);
        query.select(cb.count(parent)).where(
                cb.equal(parent.get(parentIdProperty), parentId),
                cb.equal(ownerPath.on(parent), me));
        return entityManager.createQuery(query).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Boolean> cacheOf(HttpServletRequest request) {
        if (request == null) {
            return new HashMap<>();
        }
        Object cache = request.getAttribute(CACHE_ATTRIBUTE);
        if (cache == null) {
            cache = new HashMap<Object, Boolean>();
            request.setAttribute(CACHE_ATTRIBUTE, cache);
        }
        return (Map<Object, Boolean>) cache;
    }
}
