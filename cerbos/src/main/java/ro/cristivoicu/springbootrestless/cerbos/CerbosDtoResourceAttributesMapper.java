package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.builders.AttributeValue;

import java.util.Map;

/**
 * Same purpose as {@link CerbosResourceAttributesMapper}, plus the response DTO alongside the
 * entity - for a policy condition that needs a *computed* attribute (a field that only exists on
 * the DTO, not the entity itself: a derived value, a formatted string, anything {@code
 * Mapper<E,D>} produces that the entity doesn't already carry directly).
 * <p>
 * Wired via {@link CerbosAuthorizationGuard}'s DTO-aware constructor, alongside a {@code
 * Mapper<E,D>}: the DTO is computed lazily, once per {@link CerbosAuthorizationGuard#canAccess}
 * call (never for {@code preCheck}/{@code scope}, which have no loaded entity - and so no DTO -
 * to map at all, same constraint {@link CerbosResourceAttributesMapper} already documents).
 *
 * @param <E> the entity type
 * @param <D> the response DTO type this entity maps to
 */
@FunctionalInterface
public interface CerbosDtoResourceAttributesMapper<E, D> {

    Map<String, AttributeValue> attributesOf(E entity, D dto);
}
