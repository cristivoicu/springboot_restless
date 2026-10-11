package ro.cristivoicu.springbootrestless.resource;

import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.validation.Validator;
import ro.cristivoicu.springbootrestless.annotation.RestlessDenial;
import ro.cristivoicu.springbootrestless.authorization.RestlessPrincipalIdResolver;
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
 * <p>
 * {@code includeSoftDeletedInSingleRead} (Ground rules Phase 2 item 13, {@code
 * restless.soft-delete.include-in-single-read}) - {@code true} (today's existing behavior, see
 * {@code SoftDeletable}'s own javadoc) returns a soft-deleted row from {@code findOne}/{@code
 * namedView} same as any other; {@code false} 404s instead, same as a row that was never there at
 * all. Writes/write actions on a soft-deleted row always 404, unconditionally - this flag only
 * ever affects a plain read.
 * <p>
 * {@code denyAs} is already resolved ({@code @RestlessResource(denyAs)}, else {@code
 * restless.authorization.deny-as}) - never {@link RestlessDenial#DEFAULT}. {@code
 * principalIdResolver} may be {@code null} ({@link RestlessPrincipalIdResolver#AUTHENTICATION_NAME}).
 * {@code rejectImmutableByDefault} is {@code restless.immutable.on-update=reject}.
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
        int maxBulkSize,
        boolean includeSoftDeletedInSingleRead,
        String parentPathVariable,
        String parentField,
        RestlessDenial denyAs,
        RestlessPrincipalIdResolver principalIdResolver,
        boolean rejectImmutableByDefault,
        ro.cristivoicu.springbootrestless.annotation.RestlessBulkMode bulkMode,
        ro.cristivoicu.springbootrestless.annotation.RestlessCollectionGet collectionGet
) {

    /** The pre-ownership shape: denials are 403, the default principal resolver, immutable changes ignored. */
    public RestlessInitContext(ResourceMetadata metadata, ObjectMapper objectMapper, ConversionService conversionService,
                               Validator validator, RestlessEmbedResolver embedResolver,
                               PlatformTransactionManager transactionManager, RestlessAuthorizationMetrics metrics,
                               int maxListSize, int maxPageSize, int maxBulkSize, boolean includeSoftDeletedInSingleRead,
                               String parentPathVariable, String parentField) {
        this(metadata, objectMapper, conversionService, validator, embedResolver, transactionManager, metrics,
                maxListSize, maxPageSize, maxBulkSize, includeSoftDeletedInSingleRead, parentPathVariable, parentField,
                RestlessDenial.FORBIDDEN, null, false, null, null);
    }
}
