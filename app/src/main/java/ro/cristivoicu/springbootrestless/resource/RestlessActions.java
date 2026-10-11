package ro.cristivoicu.springbootrestless.resource;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import ro.cristivoicu.springbootrestless.annotation.RestlessActionName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns action/view beans into the name-keyed maps {@code RestlessResourceHandler} serves - used by
 * generated resources for {@code @RestlessEntity(readActions/collectionActions/writeActions/views)}.
 */
public final class RestlessActions {

    private RestlessActions() {
    }

    /** Each bean keyed by its {@link RestlessActionName}; a missing or duplicate name fails startup. */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> byName(Object... beans) {
        Map<String, T> named = new LinkedHashMap<>();
        for (Object bean : beans) {
            Class<?> type = AopUtils.getTargetClass(bean);
            RestlessActionName name = AnnotationUtils.findAnnotation(type, RestlessActionName.class);
            if (name == null || name.value().isBlank()) {
                throw new IllegalStateException(type.getName() + " is listed on @RestlessEntity but has no @RestlessActionName");
            }
            if (named.put(name.value(), (T) bean) != null) {
                throw new IllegalStateException("Duplicate @RestlessActionName '" + name.value() + "' on " + type.getName());
            }
        }
        return Map.copyOf(named);
    }
}
