package ro.cristivoicu.springbootrestless.resource;

/**
 * Runtime-resolved shape of one {@link RestlessResourceHandler}: the entity/id type and
 * the per-operation DTO types it exchanges over HTTP, plus the base path it's mounted at.
 * <p>
 * Computed once per resource at startup — in Stage 1 by hand, in Stage 2 by
 * {@code RestlessRegistrar} via reflection off the resource bean's generics — and handed to
 * the resource instance so the shared handler methods know what to deserialize/convert into.
 * <p>
 * {@code createModelType}/{@code updateModelType}/{@code deleteModelType} are {@code null} when
 * {@link RestlessResourceHandler#getEnabledOperations} excludes the corresponding operation - see
 * {@link RestlessResourceHandler#resolveMetadata}'s own reasoning for why resolving them isn't
 * even attempted in that case. {@code responseDtoType} is best-effort (resolved via {@link
 * org.springframework.core.GenericTypeResolver} off {@link RestlessResourceHandler#getEntityMapper}'s
 * concrete class, same idiom as the other DTO types) and may be {@code null} too, e.g. for an
 * anonymous/lambda {@code Mapper} implementation with no reifiable generic signature - currently
 * unused by the handler methods themselves (every actual read already goes through the mapper
 * directly), only by {@code ro.cristivoicu.springbootrestless.openapi}'s default document
 * generation to describe a response schema.
 */
public record ResourceMetadata(
        String basePath,
        Class<?> entityType,
        Class<?> idType,
        Class<?> createModelType,
        Class<?> updateModelType,
        Class<?> deleteModelType,
        Class<?> searchDtoType,
        Class<?> responseDtoType
) {
}
