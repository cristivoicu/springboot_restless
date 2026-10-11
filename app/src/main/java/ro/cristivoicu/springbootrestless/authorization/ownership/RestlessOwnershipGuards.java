package ro.cristivoicu.springbootrestless.authorization.ownership;

import jakarta.persistence.EntityManager;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

/**
 * Builds {@link OwnershipAuthorizationGuard}s - the bean {@code @RestlessOwnedBy} wires into a
 * generated resource, and the one a hand-wired resource uses directly:
 * <pre>{@code
 * protected AuthorizationGuard<Event> getAuthorizationGuard() {
 *     return ownershipGuards.owned(Event.class, "ownerId");
 * }
 * }</pre>
 * Paths are validated here, when the guard is built (at startup for a generated resource) - a
 * typo is a startup failure, never a guard that silently matches nothing or everything.
 */
public class RestlessOwnershipGuards {

    private final EntityManager entityManager;

    public RestlessOwnershipGuards(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** {@code entityType} rows whose {@code ownerPath} equals the current principal id. */
    public <E> AuthorizationGuard<E> owned(Class<E> entityType, String ownerPath) {
        return new OwnershipAuthorizationGuard<>(entityManager, PropertyPath.of(entityType, ownerPath), null, null);
    }

    /**
     * {@code entityType} rows whose {@code linkPath} points at a {@code parentType} row whose
     * {@code parentOwnerPath} equals the current principal id.
     */
    public <E> AuthorizationGuard<E> ownedVia(Class<E> entityType, String linkPath, Class<?> parentType, String parentOwnerPath) {
        PropertyPath link = PropertyPath.of(entityType, linkPath);
        PropertyPath owner = PropertyPath.of(parentType, parentOwnerPath);
        PropertyPath.idPropertyOf(parentType); // fail fast: the parent must be an entity with an @Id
        return new OwnershipAuthorizationGuard<>(entityManager, owner, link, parentType);
    }
}
