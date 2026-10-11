package ro.cristivoicu.springbootrestless.authorization.ownership;

import jakarta.persistence.criteria.Path;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A field path on an entity - {@code "ownerId"}, or one association hop {@code "event.id"} -
 * resolved and validated once, then used both to read a value off a loaded entity and to build the
 * same path in a Criteria query. Resolution walks superclasses, since owner and id fields commonly
 * live on a shared {@code @MappedSuperclass}.
 */
final class PropertyPath {

    private final String expression;
    private final List<Field> segments;

    private PropertyPath(String expression, List<Field> segments) {
        this.expression = expression;
        this.segments = segments;
    }

    static PropertyPath of(Class<?> type, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Empty property path on " + type.getSimpleName());
        }
        String[] parts = expression.split("\\.");
        if (parts.length > 2) {
            throw new IllegalArgumentException("Property path '" + expression + "' on " + type.getSimpleName()
                    + " may have at most one association hop");
        }
        List<Field> segments = new ArrayList<>();
        Class<?> current = type;
        for (String part : parts) {
            Field field = findField(current, part);
            if (field == null) {
                throw new IllegalArgumentException("'" + expression + "' names no property '" + part + "' on " + current.getSimpleName());
            }
            field.setAccessible(true);
            segments.add(field);
            current = field.getType();
        }
        return new PropertyPath(expression, List.copyOf(segments));
    }

    /** The {@code @Id} field name of {@code type}, walking superclasses. */
    static String idPropertyOf(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    return field.getName();
                }
            }
        }
        throw new IllegalArgumentException(type.getSimpleName() + " has no @Id field");
    }

    Class<?> leafType() {
        return segments.get(segments.size() - 1).getType();
    }

    String expression() {
        return expression;
    }

    /** The value at this path on a loaded entity; {@code null} if any hop is {@code null}. */
    Object read(Object entity) {
        Object current = entity;
        for (Field field : segments) {
            if (current == null) {
                return null;
            }
            try {
                current = field.get(current);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not read " + expression + " on " + entity.getClass().getSimpleName(), e);
            }
        }
        return current;
    }

    <X> Path<Object> on(Path<X> root) {
        Path<Object> path = root.get(segments.get(0).getName());
        for (int i = 1; i < segments.size(); i++) {
            path = path.get(segments.get(i).getName());
        }
        return path;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
    }
}
