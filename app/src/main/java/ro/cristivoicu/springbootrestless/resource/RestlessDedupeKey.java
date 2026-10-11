package ro.cristivoicu.springbootrestless.resource;

/**
 * Duplicate detection for {@code PARTIAL} bulk create: two rows with equal keys are the same thing.
 * A new row is skipped (counted in {@code RestlessBulkResult.skipped}) when its key matches an
 * existing row the caller can see - under the same nested parent - or an earlier row of the same
 * batch. Return {@code null} for "never a duplicate".
 * <p>
 * Applied unless the request says {@code ?dedupe=false}. On the generated tier:
 * {@code @RestlessEntity(bulkMode = PARTIAL, bulkDedupe = GuestNamePhoneKey.class)}.
 */
public interface RestlessDedupeKey<E, C> {

    Object keyOfModel(C model);

    Object keyOfExisting(E existing);
}
