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
 *     <li>{@code patchDataSource} — point at a hand-written {@code PatchDataSource<Entity, Id,
 *     ?>} {@code @Component} to add a {@code PATCH} route, exactly like a hand-written resource
 *     overriding {@code getPatchDataSource()} would. Unlike the four CUD verbs, unset (the
 *     default) doesn't fall back to a generated default - {@code PATCH} is entirely opt-in, so no
 *     route gets registered for it at all until this is set. Same {@code Class<?>}-can't-name-a-
 *     parameterized-type limit as {@code authorizationGuard}: {@code DefaultPatchDataSource<E, K,
 *     P>} is generic, so point this at a small named subclass fixing its type parameters for one
 *     entity (e.g. {@code class WidgetPatchDataSource extends DefaultPatchDataSource<Widget,
 *     Long, WidgetPatchModel> { WidgetPatchDataSource(WidgetRepository r) { super(r,
 *     WidgetPatchModel.class); } }}), not at the generic class itself.</li>
 *     <li>{@code version} — this resource's API version, copied verbatim onto the generated
 *     resource's own {@code @RestlessResource(version = ...)} (see its javadoc for the syntax and
 *     what configuring a resolution strategy needs) - unlike every attribute above, this one
 *     isn't "convention vs override", it's just forwarded, since there's no per-verb naming
 *     convention for an API version to default to.</li>
 *     <li>{@code operations} — which fixed routes to actually register, mirroring {@code
 *     RestlessResourceHandler#getEnabledOperations}'s own default-to-everything/override-to-a-
 *     subset shape (see its javadoc for exactly which routes each {@link RestlessOperation} value
 *     covers, e.g. {@code CREATE} covering both {@code create} and bulk {@code createBulk}
 *     together). Defaults to every value - set explicitly to a smaller array for, say, a
 *     read-only resource: {@code operations = {RestlessOperation.READ_ONE,
 *     RestlessOperation.READ_LIST, RestlessOperation.READ_PAGE}}. <b>One real limit:</b> unlike a
 *     hand-wired resource overriding {@code getEnabledOperations()} directly, disabling an
 *     operation here doesn't relax the naming-convention requirement on its DTO - {@code
 *     createModel}/{@code updateModel} still have to resolve to something (convention or
 *     override) even with {@code CREATE}/{@code UPDATE} excluded from {@code operations}, since
 *     this attribute governs routing, not DTO resolution. A resource that should need no {@code
 *     {Entity}CreateModel} at all belongs on the manual tier instead - see
 *     [Tutorial: adding a new entity] in the README.</li>
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

    Class<?> patchDataSource() default Void.class;

    String version() default "";

    RestlessOperation[] operations() default {
            RestlessOperation.CREATE, RestlessOperation.READ_ONE, RestlessOperation.READ_LIST,
            RestlessOperation.READ_PAGE, RestlessOperation.READ_PAGE_OVERVIEW, RestlessOperation.READ_PAGE_SELECT,
            RestlessOperation.UPDATE, RestlessOperation.DELETE_ONE, RestlessOperation.DELETE_ALL
    };
}
