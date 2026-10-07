package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs a nested resource's parent-scoping (Feature B: {@code @RestlessParent}) - resolving
 * {@code field} (a flat scalar column, or one dotted {@code association.idProperty} segment),
 * reading/writing the resolved value off a loaded/about-to-be-saved entity, and building the
 * {@code Specification} every read route ANDs onto its own filter. Pure reflection throughout -
 * no {@code EntityManager}/second repository lookup, keeping this module's own "no dependency
 * beyond what {@code DataSource} already needs" shape intact even for the association form.
 */
final class ParentFieldSupport {

    private ParentFieldSupport() {
    }

    /**
     * {@code segments}: one {@link Field} for a flat scalar column (e.g. {@code eventId}), two
     * for a dotted association (e.g. {@code event} then {@code id}). {@code leafIdType}: the
     * type a path variable must convert to - the scalar field's own type, or the association's
     * own {@code @Id} field's type.
     */
    record ParentDescriptor(String pathVariable, List<Field> segments, Class<?> leafIdType) {
    }

    /** {@code null} (not nested) whenever either attribute is unset - see {@code RestlessParent}'s own javadoc for why empty means "flat". */
    static ParentDescriptor resolve(Class<?> entityType, String pathVariable, String fieldPath) {
        if (!StringUtils.hasText(pathVariable) || !StringUtils.hasText(fieldPath)) {
            return null;
        }
        String[] parts = fieldPath.split("\\.", 2);
        List<Field> segments = new ArrayList<>();

        Field first = findField(entityType, parts[0]);
        if (first == null) {
            throw new IllegalStateException("@RestlessParent field '" + fieldPath + "' names no property '"
                    + parts[0] + "' on " + entityType.getSimpleName());
        }
        segments.add(first);

        Class<?> leafIdType;
        if (parts.length == 1) {
            leafIdType = first.getType();
        } else {
            Field second = findField(first.getType(), parts[1]);
            if (second == null) {
                throw new IllegalStateException("@RestlessParent field '" + fieldPath + "' names no property '"
                        + parts[1] + "' on " + first.getType().getSimpleName());
            }
            segments.add(second);
            leafIdType = second.getType();
        }
        return new ParentDescriptor(pathVariable, List.copyOf(segments), leafIdType);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            try {
                Field field = t.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
    }

    /** Follows the (possibly dotted) chain off {@code entity} - {@code null} if an intermediate association is itself null. */
    static Object readLeafValue(Object entity, ParentDescriptor descriptor) {
        Object current = entity;
        for (Field segment : descriptor.segments()) {
            if (current == null) {
                return null;
            }
            try {
                current = segment.get(current);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not read " + segment + " for nested-resource parent scoping", e);
            }
        }
        return current;
    }

    /**
     * Writes {@code convertedPathValue} onto {@code entity} - directly for a flat scalar field,
     * or by constructing a shallow instance of the association's own type (a no-arg constructor
     * plus its {@code @Id} field set - the same "reference without a full load" shape {@code
     * EntityManager.getReference(...)} gives, built here purely via reflection) for a dotted
     * association field.
     */
    static void writeLeafValue(Object entity, ParentDescriptor descriptor, Object convertedPathValue) {
        List<Field> segments = descriptor.segments();
        try {
            if (segments.size() == 1) {
                segments.get(0).set(entity, convertedPathValue);
                return;
            }
            Field associationField = segments.get(0);
            Field idField = segments.get(1);
            Object shallow = associationField.getType().getDeclaredConstructor().newInstance();
            idField.set(shallow, convertedPathValue);
            associationField.set(entity, shallow);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not write nested-resource parent value onto " + entity.getClass(), e);
        }
    }

    /**
     * A {@link Specification} ANDing {@code root.get(seg1).get(seg2)... = convertedPathValue} -
     * the same dotted-path Criteria walk {@code CerbosQueryPlanTranslator.pathOf} already uses
     * for Cerbos-driven filters.
     */
    static <E> Specification<E> matches(ParentDescriptor descriptor, Object convertedPathValue) {
        return (root, query, cb) -> {
            Path<?> path = root;
            for (Field segment : descriptor.segments()) {
                path = path.get(segment.getName());
            }
            return cb.equal(path, convertedPathValue);
        };
    }
}
