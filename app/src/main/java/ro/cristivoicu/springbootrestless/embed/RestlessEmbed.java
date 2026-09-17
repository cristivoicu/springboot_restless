package ro.cristivoicu.springbootrestless.embed;

import ro.cristivoicu.springbootrestless.resource.RestlessResourceHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field to be populated, on request, from another resource's own read path - reusing
 * that resource's own {@code AuthorizationGuard} rather than inventing a second set of rules for
 * what's visible inside this embed. {@code GET .../{id}?expand=<name>} is the only trigger: an
 * unrequested field is left exactly as {@link #resource}'s {@code Mapper} would never have touched
 * it (whatever the DTO's own no-arg constructor leaves it at, {@code null} for a reference type),
 * so every existing caller's response shape/performance is unaffected until they opt in.
 * <p>
 * {@code sourceField} names a field on THIS resource's own entity (not its DTO) carrying the join
 * value; {@code targetField} names the field on {@code resource}'s own entity to match it against
 * - resolved as a plain equality {@code Specification}, the same natural-key-equality idiom every
 * entity in this codebase already uses in place of a JPA {@code @ManyToOne}/{@code @OneToMany}.
 * <p>
 * <b>Denial reads as empty, not 403.</b> {@link RestlessResourceHandler#findEmbeddedList}/{@link
 * RestlessResourceHandler#findEmbeddedOne} swallow a {@code preCheck}/{@code canAccess} denial
 * into an empty list / {@code null} rather than throwing - an unreadable relation must never fail
 * the outer response, only leave that one field empty. A real, deliberate consequence worth
 * knowing up front: row-scoping on the target resource (e.g. "only rows in my own department")
 * still applies here exactly as it would on that resource's own endpoint, so two different callers
 * embedding the very same entity can see different related rows - not a bug, just the same
 * scoping rule applied consistently.
 * <p>
 * {@code many = true} fields must be a {@code List<TargetDto>}; {@code many = false} (the
 * default) fields must be a single {@code TargetDto} reference - {@link RestlessEmbedResolver}
 * assigns the result via reflection, so the declared field type isn't checked beyond that at
 * compile time.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestlessEmbed {

    /** The target resource bean, e.g. {@code ProjectRestlessResource.class}. */
    Class<? extends RestlessResourceHandler<?, ?>> resource();

    /**
     * Field on this resource's own entity carrying the join value - a flat field ({@code
     * "email"}) or, to read through a {@code @ManyToOne}/{@code @OneToOne} association, a dotted
     * path ({@code "employee.id"}) - {@code null} at any hop (an unset association) means "no
     * source value", not an error: the embed resolves to an empty list / {@code null} without
     * querying anything.
     */
    String sourceField();

    /**
     * Field on the target entity to match {@link #sourceField}'s value against - a flat column
     * name ({@code "code"}) or, to join through a {@code @ManyToOne}/{@code @OneToOne}
     * association, a dotted path through it ({@code "employee.id"}, resolved as {@code
     * root.get("employee").get("id")}).
     */
    String targetField();

    /**
     * {@code false} (default): a single related object. {@code true}: a {@code List<...>} -
     * <b>only for a genuinely bounded relation</b> ("this employee's department", "this order's
     * line items" - a handful of rows, human-scale). {@link RestlessResourceHandler#findEmbeddedList}
     * has no pagination or limit at all; it returns every matching row in one response. For a
     * relation that can grow into the hundreds or thousands (a project's team, a department's
     * whole roster), don't embed it here - expose the join as its own {@code @RestlessEntity}
     * resource instead (a flat bridge entity, e.g. {@code projectId}/{@code employeeId}), so
     * {@code GET} gets real paging (page/size, same as any other resource) and adding many rows at
     * once goes through the framework's existing bulk-create route rather than one write per call.
     * "Like GraphQL, but with REST" doesn't mean unbounded either - GraphQL's own Relay connection
     * spec paginates exactly this shape of relation for exactly this reason.
     */
    boolean many() default false;

    /** The {@code expand=} key that requests this field. Empty (the default) uses the field's own name. */
    String name() default "";
}
