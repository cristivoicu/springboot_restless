package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.builders.AttributeValue;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Objects;

/**
 * Converts a plain Java value into a Cerbos {@link AttributeValue}, shared by both call sites
 * that need it: {@link CerbosPrincipalResolver} (JWT claim values) and {@link
 * CerbosResourceAttributesMapper#reflective} (POJO field values). Anything with no {@link
 * AttributeValue} equivalent (nested objects, maps, arbitrary collections) converts to {@code
 * null} rather than throwing - both call sites treat that as "skip this one", not a hard failure,
 * since a policy simply can't reference an attribute that couldn't be represented.
 */
final class CerbosAttributeValues {

    private CerbosAttributeValues() {
    }

    static AttributeValue from(Object value) {
        return switch (value) {
            case String s -> AttributeValue.stringValue(s);
            case Boolean b -> AttributeValue.boolValue(b);
            case Number n -> AttributeValue.doubleValue(n.doubleValue());
            case Enum<?> e -> AttributeValue.stringValue(e.name());
            case TemporalAccessor t -> AttributeValue.stringValue(Instant.from(t).toString());
            case Collection<?> collection -> AttributeValue.listValue(collection.stream()
                    .map(CerbosAttributeValues::from)
                    .filter(Objects::nonNull)
                    .toList());
            case null, default -> null;
        };
    }
}
