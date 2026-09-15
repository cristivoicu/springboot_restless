package ro.cristivoicu.springbootrestless.cerbos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field as maskable by {@link CerbosFieldMasker}. When a Cerbos policy's activated
 * rule for the checked action produces an {@code output} whose value contains this field's key
 * ({@link #value()}, or the Java field's own name if left blank), {@link CerbosFieldMasker} sets
 * the field to {@code null} before the DTO leaves the mapper - hiding it from whichever
 * roles/attributes the policy's output condition targets, without the mapper itself needing a
 * branch per role.
 * <p>
 * Deliberately opt-in and field-by-field, not automatic: {@code Mapper<E,D>}'s own javadoc is
 * explicit that response shaping is the one boundary this framework never reflects over
 * automatically, since it's the surface fine-grained authorization has to reason about. This
 * annotation doesn't change that - it's a tool the entity author reaches for explicitly inside
 * their own hand-written {@code Mapper}, not something that fires on its own.
 * <p>
 * Only reference types can be masked (the field is set to {@code null}); annotating a primitive
 * field throws {@link IllegalStateException} at mask time.
 *
 * @see CerbosFieldMasker
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CerbosHiddenField {

    /**
     * The key a policy's output list references for this field. Blank (the default) means "use
     * this field's own Java name".
     */
    String value() default "";
}
