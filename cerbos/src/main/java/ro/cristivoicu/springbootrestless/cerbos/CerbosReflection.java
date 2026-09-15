package ro.cristivoicu.springbootrestless.cerbos;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared "collect every instance field declared on a class and its superclasses, up to but
 * excluding {@link Object}" helper - the same idiom {@code
 * RestlessResourceHandler.getSpecification()}'s own default already uses for {@code SearchDto}
 * fields. Used by both {@link CerbosResourceAttributesMapper#reflective} (reads field values) and
 * {@link CerbosFieldMasker} (finds {@link CerbosHiddenField}-annotated ones to null out).
 */
final class CerbosReflection {

    private CerbosReflection() {
    }

    static List<Field> declaredFieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
