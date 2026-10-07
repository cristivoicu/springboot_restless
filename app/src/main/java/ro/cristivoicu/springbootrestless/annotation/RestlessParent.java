package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a resource nested under a parent path variable, e.g. {@code @RestlessResource(basePath
 * = "/events/{eventId}/guests", parent = @RestlessParent(variable = "eventId", field =
 * "eventId"))}. An attribute of {@link RestlessResource} (this tier) and, identically shaped, of
 * {@code @RestlessEntity} in the {@code processor} module (the generated tier) - two separate
 * annotation types in two separate modules, same reasoning {@code basePath}/{@code version}/
 * {@code allowAll} already duplicate across both: {@code RestlessEntityProcessor} forwards its
 * own copy's attributes verbatim onto this one when it generates a resource class (see its own
 * javadoc).
 * <p>
 * {@code variable} must appear in the resource's own {@code basePath} as a {@code {variable}}
 * path segment - checked at startup by {@code RestlessRegistrar} (an {@code IllegalStateException}
 * naming the resource, not a silently unscoped route, if it doesn't - "fail closed" per the Ground
 * rules for this feature). {@code field} names the entity property the resolved path value is
 * read from/written to: either a scalar column directly on the entity (e.g. {@code "eventId"}, a
 * flat {@code UUID}/{@code Long} foreign-key column - the common case) or a single dotted segment
 * naming a {@code @ManyToOne}/{@code @OneToOne} association plus its own {@code @Id} property
 * (e.g. {@code "event.id"}). Both forms are resolved purely via reflection, never {@code
 * EntityManager}/a second repository lookup - see {@code ParentFieldSupport} for exactly how.
 * <p>
 * Leaving this unset (the default, {@code variable = ""}/{@code field = ""}) means exactly what it
 * always has: a flat, non-nested resource - every nested-resource-specific code path in {@code
 * RestlessResourceHandler} is a no-op unless this is set.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessParent {

    /** The path variable name in the resource's own {@code basePath}, e.g. {@code "eventId"} for {@code basePath = "/events/{eventId}/guests"}. Empty (the default) means "not nested". */
    String variable() default "";

    /**
     * The entity property the resolved path value is read from/written to - a flat scalar field
     * name, or one dotted segment naming an association plus its own {@code @Id} property (e.g.
     * {@code "event.id"}). Empty (the default) means "not nested".
     */
    String field() default "";
}
