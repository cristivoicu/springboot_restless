package ro.cristivoicu.springbootrestless.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The read-only counterpart of {@link RestlessEntity}: placed on the entity, it makes {@code
 * RestlessEntityProcessor} generate a {@code {Entity}RestlessResource} extending {@code
 * RestlessReadResourceHandler} - only {@code GET} routes, and only the read artifact ({@code
 * spring-boot-restless-read}) needed on the classpath. The entity needs just {@code
 * {Entity}SearchDto} and {@code {Entity}Dto} (or a hand-written {@code {Entity}Mapper}); no
 * create/update model, no write data source.
 * <p>
 * Every attribute means exactly what the same-named {@link RestlessEntity} attribute means. The
 * write-only ones ({@code createModel}, {@code updateModel}, the create/update/delete/patch data
 * sources, {@code writeActions}, {@code hooks}, {@code multipartCreate}, {@code bulkMode}, {@code
 * bulkDedupe}) simply don't exist here. {@code operations} defaults to every read operation and
 * may only name read operations - a write one is a compile error.
 * <p>
 * A {@code @RestlessEntity} whose {@code operations} list no write operation and which sets no
 * write-only attribute is generated read-only too - this annotation just makes that intent
 * explicit and drops the attributes that can't apply. {@code SOURCE} retention, same as {@link
 * RestlessEntity}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface RestlessReadEntity {

    String basePath();

    Class<?> searchDto() default Void.class;

    Class<?> dto() default Void.class;

    Class<?> mapper() default Void.class;

    Class<?> readDataSource() default Void.class;

    Class<?> authorizationGuard() default Void.class;

    String version() default "";

    boolean allowAll() default false;

    RestlessOperation[] operations() default {
            RestlessOperation.READ_ONE, RestlessOperation.READ_LIST, RestlessOperation.READ_PAGE,
            RestlessOperation.READ_PAGE_OVERVIEW, RestlessOperation.READ_PAGE_SELECT
    };

    RestlessParent parent() default @RestlessParent;

    RestlessDenial denyAs() default RestlessDenial.DEFAULT;

    Class<?>[] readActions() default {};

    Class<?>[] collectionActions() default {};

    Class<?>[] views() default {};

    RestlessCollectionGet collectionGet() default RestlessCollectionGet.DEFAULT;

    Class<?> repository() default Void.class;
}
