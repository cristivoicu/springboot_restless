package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.builders.AttributeValue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes whichever fields of an entity {@code E} its Cerbos policies need, mirroring {@code
 * Mapper<E,?>}'s existing "author writes the shape, framework calls it" pattern. Implemented by
 * the entity author and handed to {@link CerbosAuthorizationGuard}'s constructor; called once per
 * {@code canAccess} check (never for {@code scope} - a query plan is evaluated kind-wide, before
 * any single instance is loaded).
 *
 * @param <E> the entity type this mapper describes to Cerbos
 */
@FunctionalInterface
public interface CerbosResourceAttributesMapper<E> {

    Map<String, AttributeValue> attributesOf(E entity);

    CerbosResourceAttributesMapper<?> NONE = entity -> Map.of();

    @SuppressWarnings("unchecked")
    static <E> CerbosResourceAttributesMapper<E> none() {
        return (CerbosResourceAttributesMapper<E>) NONE;
    }

    /**
     * Field-by-field POJO access with no hand-written mapping at all: reflects over every
     * instance field declared on {@code entityType} (and its superclasses, up to but excluding
     * {@link Object}) once, then reads each one per entity - the same "reflect over declared
     * fields, skip what doesn't apply" idiom {@code RestlessResourceHandler.getSpecification()}'s
     * own default already uses for {@code SearchDto} fields. A policy can then reference {@code
     * request.resource.attr.<anyFieldName>} for any field whose value converts to an {@link
     * AttributeValue} (string/boolean/number/enum/temporal/collection thereof - see {@link
     * CerbosAttributeValues}); fields that don't (nested objects, maps, ...) are silently
     * omitted, not failed - a policy just can't reference those.
     * <p>
     * Reasonable as a default for flat entities; write a mapper by hand instead where a policy
     * needs a computed or renamed attribute, or where reflecting every field would expose more
     * than a policy should see.
     */
    static <E> CerbosResourceAttributesMapper<E> reflective(Class<E> entityType) {
        List<Field> fields = CerbosReflection.declaredFieldsOf(entityType);
        return entity -> {
            Map<String, AttributeValue> attributes = new HashMap<>();
            for (Field field : fields) {
                Object value;
                try {
                    value = field.get(entity);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Could not read " + field + " for Cerbos resource attributes", e);
                }
                AttributeValue attributeValue = CerbosAttributeValues.from(value);
                if (attributeValue != null) {
                    attributes.put(field.getName(), attributeValue);
                }
            }
            return attributes;
        };
    }
}
