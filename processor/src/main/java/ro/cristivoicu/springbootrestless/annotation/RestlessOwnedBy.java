package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * "A user manages only what they own", declared on a {@link RestlessEntity} instead of written as
 * a guard class. The generated resource gets an {@code OwnershipAuthorizationGuard} built by the
 * {@code RestlessOwnershipGuards} bean - lists scoped in SQL, every single row checked, a create
 * pointing at someone else's parent rolled back. Pair it with {@code denyAs = NOT_FOUND} so other
 * people's ids can't be probed.
 * <p>
 * Two shapes - set exactly one:
 * <pre>{@code
 * // the row carries its owner
 * @RestlessOwnedBy(field = "ownerId")
 * public class Event { @RestlessFromPrincipal private UUID ownerId; ... }
 *
 * // the row belongs to a parent that carries the owner
 * @RestlessOwnedBy(path = "eventId", via = Event.class, ownerField = "ownerId")
 * public class Guest { private UUID eventId; ... }
 * }</pre>
 * Paths may have one association hop ({@code "event.id"}, {@code "owner.id"}). The principal id
 * comes from the {@code RestlessPrincipalIdResolver} bean and is converted to the owner field's
 * type. Can't be combined with {@link RestlessEntity#authorizationGuard()} - that's a compile error.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface RestlessOwnedBy {

    /** Direct ownership: the owner field on this entity. */
    String field() default "";

    /** Via a parent: the field on this entity holding the parent's id. */
    String path() default "";

    /** Via a parent: the parent entity type. */
    Class<?> via() default Void.class;

    /** Via a parent: the owner field on {@link #via()}. */
    String ownerField() default "";
}
