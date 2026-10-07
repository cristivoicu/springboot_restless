package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time counterpart of {@code app}'s own {@code RestlessParent} - see its javadoc for the
 * full shape/semantics (both are identical; this module can't depend on {@code app}, the same
 * reason {@code basePath}/{@code version}/{@code allowAll} are each declared twice too). {@code
 * RestlessEntityProcessor} validates {@code field} at compile time (the property/dotted
 * association+id exists, its type is convertible from a path variable) and forwards both
 * attributes verbatim onto the generated resource's own {@code @RestlessResource(parent = ...)}.
 * <p>
 * {@code SOURCE} retention, same as {@link RestlessEntity} itself - a pure compile-time signal.
 */
@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface RestlessParent {

    /** The path variable name in the entity's own {@code basePath}, e.g. {@code "eventId"}. Empty (the default) means "not nested". */
    String variable() default "";

    /**
     * The entity property the resolved path value is read from/written to - a flat scalar field
     * name, or one dotted segment naming an association plus its own {@code @Id} property (e.g.
     * {@code "event.id"}). Empty (the default) means "not nested".
     */
    String field() default "";
}
