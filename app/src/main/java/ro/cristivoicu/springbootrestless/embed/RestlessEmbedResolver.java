package ro.cristivoicu.springbootrestless.embed;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Populates every {@code expand=}-requested {@link RestlessEmbed} field on an already-mapped DTO.
 * Deliberately runs <em>after</em> {@code Mapper.map(...)} rather than inside it - a generic,
 * reflection-driven step every resource on every tier gets for free the moment its DTO declares a
 * {@link RestlessEmbed} field, with zero per-entity {@code Mapper} code, the same "framework does
 * the boilerplate, entity author only declares" shape {@code getEnabledOperations()}/{@code
 * patchDataSource} already follow.
 * <p>
 * {@link #NONE} is the default every {@code RestlessResourceHandler} is wired with until {@code
 * RestlessRegistrar} supplies the real, {@link ApplicationContext}-backed instance via {@code
 * init(...)} - a no-op, not a null, so {@code findOne()} never has to null-check it.
 */
@Component
public class RestlessEmbedResolver {

    public static final RestlessEmbedResolver NONE = new RestlessEmbedResolver(null);

    private final ApplicationContext applicationContext;

    public RestlessEmbedResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * {@code dto}: the already-mapped response object to populate in place. {@code sourceEntity}:
     * the raw entity {@code dto} was mapped from - {@link RestlessEmbed#sourceField()} is read off
     * this, not off {@code dto}, since the join key doesn't have to be a field the DTO itself
     * exposes.
     */
    public void resolve(Object dto, Object sourceEntity, HttpServletRequest request) {
        if (applicationContext == null || dto == null) {
            return;
        }
        Set<String> requested = requestedNames(request);
        if (requested.isEmpty()) {
            return;
        }

        for (Field field : declaredFieldsOf(dto.getClass())) {
            RestlessEmbed annotation = field.getAnnotation(RestlessEmbed.class);
            if (annotation == null) {
                continue;
            }
            String name = annotation.name().isEmpty() ? field.getName() : annotation.name();
            if (!requested.contains(name)) {
                continue;
            }
            populate(dto, field, annotation, sourceEntity, request);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void populate(Object dto, Field field, RestlessEmbed annotation, Object sourceEntity, HttpServletRequest request) {
        Object joinValue = readField(sourceEntity, annotation.sourceField());
        if (joinValue == null) {
            // No source value at all (e.g. an unset @ManyToOne on this side) - nothing to match,
            // same "absent means no match, not an error" reasoning ProjectAuthorizationGuardBean
            // already applies to a missing departmentCode. Skip the query entirely.
            setField(dto, field, annotation.many() ? List.of() : null);
            return;
        }

        RestlessResourceHandler targetHandler = applicationContext.getBean(annotation.resource());
        Specification joinFilter = (root, query, cb) -> cb.equal(pathOf(root, annotation.targetField()), joinValue);
        Object result = annotation.many()
                ? targetHandler.findEmbeddedList(joinFilter, request)
                : targetHandler.findEmbeddedOne(joinFilter, request);
        setField(dto, field, result);
    }

    /**
     * {@link RestlessEmbed#targetField()} as a JPA Criteria path, splitting on {@code "."} so a
     * join through an association works too (e.g. {@code "employee.id"} - {@code
     * root.get("employee").get("id")} - not just a flat column like {@code "code"}).
     */
    private static Path<?> pathOf(Root<?> root, String dottedField) {
        Path<?> path = root;
        for (String segment : dottedField.split("\\.")) {
            path = path.get(segment);
        }
        return path;
    }

    private static Set<String> requestedNames(HttpServletRequest request) {
        String raw = request.getParameter("expand");
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * {@link RestlessEmbed#sourceField()}, dotted-path aware like {@link #pathOf} - {@code
     * "employee.id"} reads {@code target.getEmployee()} then, only if that wasn't {@code null},
     * its own {@code id} - short-circuiting to {@code null} (not a {@link NullPointerException})
     * the moment an intermediate hop is unset, the same "absent means no match" reasoning {@link
     * #populate} already applies to the final value.
     */
    private static Object readField(Object target, String dottedFieldName) {
        Object current = target;
        for (String segment : dottedFieldName.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = readDeclaredField(current, segment);
        }
        return current;
    }

    /**
     * Via the getter method (Lombok's {@code @Getter} convention), not raw {@link Field} access -
     * deliberately, unlike {@link #declaredFieldsOf}'s DTO scan below: {@code target} here can be
     * a lazy JPA {@code @ManyToOne} value, and Hibernate represents an uninitialized one as a
     * runtime proxy subclass that intercepts <em>getter calls</em>, not raw field reads - reading
     * the field directly on such a proxy silently returns the wrong (unset) value instead of
     * triggering proper resolution. {@link Class#getMethod} (public, inherited included) rather
     * than {@code getDeclaredMethod}, so whatever override the proxy subclass provides is what
     * actually runs.
     */
    private static Object readDeclaredField(Object target, String fieldName) {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("@RestlessEmbed field '" + fieldName + "' has no " + getterName
                    + "() on " + target.getClass(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + fieldName + " off " + target.getClass(), e);
        }
    }

    private static void setField(Object dto, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(dto, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not set " + field, e);
        }
    }

    /** Same "every declared field, own class and superclasses, statics excluded" idiom used elsewhere. */
    private static List<Field> declaredFieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
