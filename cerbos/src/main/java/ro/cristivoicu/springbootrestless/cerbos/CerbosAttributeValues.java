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

    // Ground rules Phase 3 item 16: type patterns in a switch (JEP 441) need Java 21 - this
    // module builds down to Java 17, so a plain instanceof chain stands in for what would
    // otherwise have been a switch over value's type, same case order/behavior either way.
    static AttributeValue from(Object value) {
        if (value instanceof String s) {
            return AttributeValue.stringValue(s);
        }
        if (value instanceof Boolean b) {
            return AttributeValue.boolValue(b);
        }
        if (value instanceof Number n) {
            return AttributeValue.doubleValue(n.doubleValue());
        }
        if (value instanceof Enum<?> e) {
            return AttributeValue.stringValue(e.name());
        }
        if (value instanceof TemporalAccessor t) {
            return AttributeValue.stringValue(Instant.from(t).toString());
        }
        if (value instanceof Collection<?> collection) {
            return AttributeValue.listValue(collection.stream()
                    .map(CerbosAttributeValues::from)
                    .filter(Objects::nonNull)
                    .toList());
        }
        return null;
    }
}
