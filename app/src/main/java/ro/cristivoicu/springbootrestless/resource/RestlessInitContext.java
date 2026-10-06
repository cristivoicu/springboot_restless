package ro.cristivoicu.springbootrestless.resource;

import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.validation.Validator;
import ro.cristivoicu.springbootrestless.embed.RestlessEmbedResolver;
import ro.cristivoicu.springbootrestless.metrics.RestlessAuthorizationMetrics;
import tools.jackson.databind.ObjectMapper;

/**
 * Everything {@link RestlessResourceHandler#init} needs to wire a resource's infra collaborators -
 * one record instead of the telescoping chain of {@code init(...)} overloads this replaces
 * (Ground rules Phase 2 item 14). Each overload existed purely so an older caller/test kept
 * compiling unchanged as one more collaborator was added over time; a record makes every field
 * named at the call site instead, so a new one (see {@code restless.soft-delete.include-in-single-read},
 * item 13) is just one more component here, not a seventh overload.
 * <p>
 * {@code transactionManager} may be {@code null} (no transaction boundary at all - see {@code
 * RestlessResourceHandler#inTransaction}'s own javadoc for why that's a deliberate, not broken,
 * fallback); every other field is expected non-null, with {@code embedResolver}/{@code metrics}
 * each having their own shared no-op singleton ({@link RestlessEmbedResolver#NONE}/{@link
 * RestlessAuthorizationMetrics#NONE}) for a caller with nothing real to supply.
 */
public record RestlessInitContext(
        ResourceMetadata metadata,
        ObjectMapper objectMapper,
        ConversionService conversionService,
        Validator validator,
        RestlessEmbedResolver embedResolver,
        PlatformTransactionManager transactionManager,
        RestlessAuthorizationMetrics metrics,
        int maxListSize,
        int maxPageSize,
        int maxBulkSize
) {
}
