package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Placed on the entity itself. At compile time, {@code RestlessEntityProcessor} generates the
 * {@code {Entity}RestlessResource} glue class the runtime layer needs — the entity author only
 * has to write the entity, its DTOs, and its {@code Mapper} (the DDD-meaningful, hand-written
 * part), following a naming convention: {@code {Entity}CreateModel}, {@code {Entity}UpdateModel},
 * {@code {Entity}SearchDto}, {@code {Entity}Mapper}, {@code {Entity}Repository}, all in the same
 * package as the entity. A missing {@code {Entity}Repository} is generated too.
 * <p>
 * Every attribute below defaults to "use the convention" ({@code createModel}/{@code
 * updateModel}/{@code searchDto}/{@code mapper}) or "use the framework's default, reflection-based
 * implementation" ({@code *DataSource}) — set one explicitly only where that isn't enough:
 * <ul>
 *     <li>{@code createModel}/{@code updateModel}/{@code searchDto}/{@code mapper} — point at a
 *     class that doesn't follow the naming convention (different name, different package).</li>
 *     <li>{@code createDataSource}/{@code readDataSource}/{@code updateDataSource}/{@code
 *     deleteDataSource} — point at a hand-written {@code @Component} bean instead of generating
 *     a {@code Default*DataSource} call for that one verb (computed fields, related-entity
 *     lookups, custom fetch logic, ...). The generated resource injects it as a constructor
 *     parameter, exactly like a hand-written resource bean would.</li>
 * </ul>
 * <p>
 * {@code SOURCE} retention: this is a pure compile-time signal, never read at runtime (unlike
 * {@code RestlessResource}, which the generated class also carries, and which {@code
 * RestlessRegistrar} does read at runtime).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface RestlessEntity {

    String basePath();

    Class<?> createModel() default Void.class;

    Class<?> updateModel() default Void.class;

    Class<?> searchDto() default Void.class;

    Class<?> mapper() default Void.class;

    Class<?> createDataSource() default Void.class;

    Class<?> readDataSource() default Void.class;

    Class<?> updateDataSource() default Void.class;

    Class<?> deleteDataSource() default Void.class;
}
