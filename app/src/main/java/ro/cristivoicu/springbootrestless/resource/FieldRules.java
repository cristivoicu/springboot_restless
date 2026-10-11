package ro.cristivoicu.springbootrestless.resource;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.annotation.RestlessFromPrincipal;
import ro.cristivoicu.springbootrestless.annotation.RestlessImmutable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An entity's server-managed fields - {@link RestlessFromPrincipal} and {@link RestlessImmutable} -
 * resolved once per resource in {@code RestlessResourceHandler#init} and enforced on every write.
 * {@link #NONE} (the overwhelmingly common case) makes every method here a no-op.
 * <p>
 * Write pipeline, per entity:
 * <ul>
 *   <li>create: {@link #applyPrincipal} writes every principal field (pre-save from
 *       {@code DefaultCreateDataSource} via {@link RestlessContext#applyServerFieldsTo}, and again
 *       by the handler after any data source, as the guarantee);</li>
 *   <li>update/patch: {@link #snapshot} the stored values before the data source runs, {@link
 *       #rejectChanges} against the body for {@code REJECT} fields, {@link #restore} afterwards.</li>
 * </ul>
 */
final class FieldRules {

    static final FieldRules NONE = new FieldRules(List.of(), List.of());

    private record ImmutableField(Field field, boolean reject) {
    }

    private final List<Field> principalFields;
    private final List<ImmutableField> immutableFields;

    private FieldRules(List<Field> principalFields, List<ImmutableField> immutableFields) {
        this.principalFields = principalFields;
        this.immutableFields = immutableFields;
    }

    /**
     * @param rejectByDefault what {@link RestlessImmutable.OnChange#DEFAULT} means for this app
     *                        ({@code restless.immutable.on-update})
     */
    static FieldRules resolve(Class<?> entityType, boolean rejectByDefault) {
        List<Field> principal = new ArrayList<>();
        List<ImmutableField> immutable = new ArrayList<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                boolean fromPrincipal = field.isAnnotationPresent(RestlessFromPrincipal.class);
                RestlessImmutable rule = field.getAnnotation(RestlessImmutable.class);
                if (!fromPrincipal && rule == null) {
                    continue;
                }
                field.setAccessible(true);
                if (fromPrincipal) {
                    principal.add(field);
                }
                // A principal field is immutable too. Attempts to change it are ignored rather than
                // rejected: the processor already refuses a model that declares it, so only a
                // hand-written data source could try.
                boolean reject = rule != null && (rule.onUpdate() == RestlessImmutable.OnChange.REJECT
                        || (rule.onUpdate() == RestlessImmutable.OnChange.DEFAULT && rejectByDefault));
                immutable.add(new ImmutableField(field, reject));
            }
        }
        return principal.isEmpty() && immutable.isEmpty() ? NONE : new FieldRules(List.copyOf(principal), List.copyOf(immutable));
    }

    boolean isEmpty() {
        return this == NONE;
    }

    List<String> principalFieldNames() {
        return principalFields.stream().map(Field::getName).toList();
    }

    /** Writes the principal into every {@link RestlessFromPrincipal} field; {@code 401} if there is no principal. */
    void applyPrincipal(Object entity, RestlessContext context, ConversionService conversionService) {
        if (principalFields.isEmpty() || entity == null) {
            return;
        }
        Object principal = context.principalId();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        for (Field field : principalFields) {
            try {
                field.set(entity, conversionService.convert(principal, field.getType()));
            } catch (ConversionException e) {
                throw new IllegalStateException("Principal id " + principal + " can't be converted to "
                        + field.getDeclaringClass().getSimpleName() + "." + field.getName() + " (" + field.getType().getSimpleName() + ")", e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not write " + field, e);
            }
        }
    }

    /** Stored values of every immutable field, read before an update/patch data source runs. */
    Map<Field, Object> snapshot(Object entity) {
        if (immutableFields.isEmpty() || entity == null) {
            return Map.of();
        }
        Map<Field, Object> values = new LinkedHashMap<>();
        for (ImmutableField immutable : immutableFields) {
            values.put(immutable.field(), read(immutable.field(), entity));
        }
        return values;
    }

    /** {@code 400} if the body carries a non-null, different value for a {@code REJECT} field. */
    void rejectChanges(Object body, Map<Field, Object> snapshot) {
        if (body == null) {
            return;
        }
        for (ImmutableField immutable : immutableFields) {
            if (!immutable.reject()) {
                continue;
            }
            String name = immutable.field().getName();
            Object sent = bodyValue(body, name);
            if (sent != null && !Objects.equals(sent, snapshot.get(immutable.field()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field '" + name + "' cannot be changed");
            }
        }
    }

    /** Puts every immutable field back to its {@link #snapshot} value, whatever the data source did. */
    void restore(Object entity, Map<Field, Object> snapshot) {
        if (snapshot.isEmpty() || entity == null) {
            return;
        }
        snapshot.forEach((field, value) -> {
            try {
                field.set(entity, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not restore " + field, e);
            }
        });
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read " + field, e);
        }
    }

    /** A same-named property of an update/patch body: a record component, a getter, or a plain field. */
    private static Object bodyValue(Object body, String name) {
        Class<?> type = body.getClass();
        try {
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    if (component.getName().equals(name)) {
                        return component.getAccessor().invoke(body);
                    }
                }
                return null;
            }
            String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (String getter : new String[]{"get" + capitalized, "is" + capitalized}) {
                try {
                    Method method = type.getMethod(getter);
                    return method.invoke(body);
                } catch (NoSuchMethodException ignored) {
                    // try the next shape
                }
            }
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(body);
                } catch (NoSuchFieldException ignored) {
                    // keep walking up
                }
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read '" + name + "' from " + type.getSimpleName(), e);
        }
    }
}
