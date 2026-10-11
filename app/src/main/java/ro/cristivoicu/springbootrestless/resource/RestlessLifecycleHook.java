package ro.cristivoicu.springbootrestless.resource;

/**
 * Side effects around a resource's writes that don't justify replacing a whole data source -
 * deleting stored files with the row, publishing an event, invalidating a cache.
 * <p>
 * Every hook runs inside the write's transaction, after authorization succeeded for that row -
 * a hook never sees a row the caller wasn't allowed to touch - and an exception from a hook
 * rolls the whole write back. Composes with the default data sources and with hand-written ones.
 * Bulk routes call the same hooks once per row. On the generated tier, list the beans in
 * {@code @RestlessEntity(hooks = ...)}; a hand-wired resource overrides
 * {@code RestlessResourceHandler#getLifecycleHooks()}.
 * <p>
 * Side effects outside the database (object storage, messages) can't be rolled back with it:
 * do the reversible part last, or accept the documented ordering.
 */
public interface RestlessLifecycleHook<E> {

    /** After the row was created and authorized, before the flush. Server fields (parent, principal) are already set. */
    default void afterCreate(E created, RestlessContext context) throws Exception {
    }

    /** Before an update/patch data source runs, on the stored row. */
    default void beforeUpdate(E existing, RestlessContext context) throws Exception {
    }

    /** After an update/patch was applied and authorized, before the flush. */
    default void afterUpdate(E updated, RestlessContext context) throws Exception {
    }

    /** Before the row is deleted - the last moment its related data is still there. */
    default void beforeDelete(E existing, RestlessContext context) throws Exception {
    }

    /** After the delete was flushed; {@code deleted} is detached. */
    default void afterDelete(E deleted, RestlessContext context) throws Exception {
    }
}
