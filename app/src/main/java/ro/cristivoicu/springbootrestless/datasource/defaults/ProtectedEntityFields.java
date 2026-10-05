package ro.cristivoicu.springbootrestless.datasource.defaults;

import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import ro.cristivoicu.springbootrestless.datasource.SoftDeletable;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Property names {@code Default{Create,Update,Patch}DataSource} must never let {@code
 * BeanUtils.copyProperties} write into, no matter what a client-controlled DTO declares: {@code
 * @Id}, {@code @Version}, {@link SoftDeletable}'s {@code deleted}, and the two Spring Data
 * auditing annotations - walked up the entity's own class hierarchy the same way {@code
 * RestlessResourceHandler#entityPropertyNames}/{@code #readVersion} already do, since any of
 * these can live on a shared {@code @MappedSuperclass} (e.g. {@code AbstractAuditableEntity})
 * rather than the concrete entity itself.
 * <p>
 * {@code copyProperties} matches source and target purely by property <em>name</em>, so simply
 * never being a legitimate field on a well-formed {@code CreateModel}/{@code UpdateModel}/{@code
 * PatchModel} is no protection at all - a hostile client is the one deciding what the request
 * body contains, not the DTO author. See {@code MassAssignmentProtectionTest} for the concrete
 * failure this prevents (a populated {@code id} on create merging onto an existing row instead of
 * inserting a new one, entirely bypassing that row's {@code UPDATE} guard).
 */
final class ProtectedEntityFields {

    private ProtectedEntityFields() {
    }

    static String[] of(Class<?> entityType) {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)
                        || field.isAnnotationPresent(Version.class)
                        || field.isAnnotationPresent(CreatedDate.class)
                        || field.isAnnotationPresent(LastModifiedDate.class)) {
                    names.add(field.getName());
                }
            }
        }
        if (SoftDeletable.class.isAssignableFrom(entityType)) {
            names.add("deleted");
        }
        return names.toArray(new String[0]);
    }
}
