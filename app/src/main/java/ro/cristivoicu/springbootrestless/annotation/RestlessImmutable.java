package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field that's set on create and never changes afterwards - a contract number, a slug, the kind
 * of a polymorphic row.
 * <p>
 * On update/patch the stored value always survives, whatever the update model or a hand-written
 * data source did with it. What happens when the request <em>tries</em> to change it is
 * {@link #onUpdate()}:
 * <ul>
 *   <li>{@link OnChange#IGNORE} - the attempt is silently dropped;</li>
 *   <li>{@link OnChange#REJECT} - a {@code 400} naming the field, nothing written;</li>
 *   <li>{@link OnChange#DEFAULT} - {@code restless.immutable.on-update}, itself {@code IGNORE}.</li>
 * </ul>
 * "Tries to change" means the update/patch body carries a same-named property with a non-null value
 * different from the stored one; {@code null} counts as "not sent" (PATCH semantics, and a PUT body
 * that simply omits it).
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessImmutable {

    OnChange onUpdate() default OnChange.DEFAULT;

    enum OnChange {
        DEFAULT,
        IGNORE,
        REJECT
    }
}
