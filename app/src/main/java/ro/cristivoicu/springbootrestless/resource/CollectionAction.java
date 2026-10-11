package ro.cristivoicu.springbootrestless.resource;

import org.springframework.data.jpa.domain.Specification;

/**
 * A named read over the whole collection that isn't a page of rows - a summary, a count, a
 * download URL, an export: {@code GET {basePath}/actions/{name}}.
 * <p>
 * {@code visibleRows} is exactly what this caller may see: the nested parent filter, the
 * guard's {@code scope()}, and the soft-delete filter, already ANDed. Use it with the entity's
 * repository, and nothing the caller can't list will ever leak into the result. Runs inside a
 * read-only transaction. Return any JSON-serializable value, or a {@link RestlessBinary}.
 * <p>
 * Checked against {@code AuthorizationGuard.Action.CUSTOM_READ} with the action's name - the same
 * action a {@link ReadAction} uses; names are shared between the two (a clash is a startup error).
 * On the generated tier, list the bean in {@code @RestlessEntity(collectionActions = ...)} and name
 * it with {@code @RestlessActionName}.
 */
@FunctionalInterface
public interface CollectionAction<E> {

    Object execute(Specification<E> visibleRows, RestlessContext context) throws Exception;
}
