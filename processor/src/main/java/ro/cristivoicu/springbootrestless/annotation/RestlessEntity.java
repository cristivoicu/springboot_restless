package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Placed on the entity itself. At compile time, {@code RestlessEntityProcessor} generates the
 * {@code {Entity}RestlessResource} glue class the runtime layer needs — the entity author only
 * has to write the entity and its DTOs (the DDD-meaningful, always-hand-written part: response
 * *shape* stays a deliberate decision — see {@code Mapper}'s javadoc), following a naming
 * convention: {@code {Entity}CreateModel}, {@code {Entity}UpdateModel}, {@code {Entity}SearchDto},
 * {@code {Entity}Dto}, all in the same package as the entity. A missing {@code {Entity}Repository}
 * is generated too, and so is a missing {@code {Entity}Mapper} — see {@code mapper}'s javadoc
 * below and {@link RestlessMapperExclude} for the one piece of per-field control that still needs.
 * <p>
 * Every attribute below defaults to "use the convention" ({@code createModel}/{@code
 * updateModel}/{@code searchDto}/{@code mapper}/{@code dto}) or "use the framework's default,
 * reflection-based implementation" ({@code *DataSource}, and now {@code mapper} too) — set one
 * explicitly only where that isn't enough:
 * <ul>
 *     <li>{@code createModel}/{@code updateModel}/{@code searchDto}/{@code dto} — point at a
 *     class that doesn't follow the naming convention (different name, different package).</li>
 *     <li>{@code mapper} — point at a hand-written {@code Mapper<Entity, ?>} {@code @Component}
 *     instead of generating a reflective, {@code BeanUtils.copyProperties}-based one for logic a
 *     field-by-field copy can't express (computed/renamed fields, masking, ...). Same escape
 *     hatch {@code createDataSource} etc. already offer for their verb.</li>
 *     <li>{@code createDataSource}/{@code readDataSource}/{@code updateDataSource}/{@code
 *     deleteDataSource} — point at a hand-written {@code @Component} bean instead of generating
 *     a {@code Default*DataSource} call for that one verb (computed fields, related-entity
 *     lookups, custom fetch logic, ...). The generated resource injects it as a constructor
 *     parameter, exactly like a hand-written resource bean would.</li>
 *     <li>{@code authorizationGuard} — point at a hand-written {@code AuthorizationGuard<Entity>}
 *     {@code @Component} to inject into the generated resource, exactly like a hand-written
 *     resource bean overriding {@code getAuthorizationGuard()} would. Unset (the default) means
 *     what it always has: no override at all, so {@code RestlessResourceHandler}'s own
 *     default-permissive {@code AuthorizationGuard.allowAll()} applies. Since this is a
 *     {@code Class<?>} attribute, it names one concrete class, not a parameterized type — a
 *     generic guard implementation meant to back more than one entity (e.g. {@code
 *     CerbosAuthorizationGuard<E>}) needs a small named {@code @Component} per entity that
 *     implements {@code AuthorizationGuard<Entity>} and delegates to it, rather than pointing
 *     this attribute at the generic class itself.</li>
 *     <li>{@code version} — this resource's API version, copied verbatim onto the generated
 *     resource's own {@code @RestlessResource(version = ...)} (see its javadoc for the syntax and
 *     what configuring a resolution strategy needs) - unlike every attribute above, this one
 *     isn't "convention vs override", it's just forwarded, since there's no per-verb naming
 *     convention for an API version to default to.</li>
 * </ul>
 * <p>
 * {@code SOURCE} retention: this is a pure compile-time signal, never read at runtime (unlike
 * {@code RestlessResource}, which the generated class also carries, and which {@code
 * RestlessRegistrar} does read at runtime) - {@code version} above is exactly why the generated
 * class needs to carry its own {@code @RestlessResource}, rather than {@code RestlessRegistrar}
 * reading this annotation directly: this one doesn't exist any more once compilation finishes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface RestlessEntity {

    String basePath();

    Class<?> createModel() default Void.class;

    Class<?> updateModel() default Void.class;

    Class<?> searchDto() default Void.class;

    Class<?> dto() default Void.class;

    Class<?> mapper() default Void.class;

    Class<?> createDataSource() default Void.class;

    Class<?> readDataSource() default Void.class;

    Class<?> updateDataSource() default Void.class;

    Class<?> deleteDataSource() default Void.class;

    Class<?> authorizationGuard() default Void.class;

    String version() default "";
}
