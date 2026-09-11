package ro.cristivoicu.springbootrestless.resource;

/**
 * Runtime-resolved shape of one {@link RestlessResourceHandler}: the entity/id type and
 * the per-operation DTO types it exchanges over HTTP, plus the base path it's mounted at.
 * <p>
 * Computed once per resource at startup — in Stage 1 by hand, in Stage 2 by
 * {@code RestlessRegistrar} via reflection off the resource bean's generics — and handed to
 * the resource instance so the shared handler methods know what to deserialize/convert into.
 */
public record ResourceMetadata(
        String basePath,
        Class<?> entityType,
        Class<?> idType,
        Class<?> createModelType,
        Class<?> updateModelType,
        Class<?> deleteModelType,
        Class<?> searchDtoType
) {
}
