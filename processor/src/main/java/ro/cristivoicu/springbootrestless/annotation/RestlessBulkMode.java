package ro.cristivoicu.springbootrestless.annotation;

/**
 * How {@code POST {basePath}/bulk} treats a batch with bad rows.
 * <ul>
 *   <li>{@link #ALL_OR_NOTHING} (default) - one invalid or denied row fails the whole request,
 *       nothing is written;</li>
 *   <li>{@link #PARTIAL} - invalid rows, rows a data source rejects, and duplicates (with a
 *       {@code RestlessDedupeKey}) are reported per row; the valid ones are created. The response is
 *       a {@code RestlessBulkResult}. A database failure or an authorization denial still aborts
 *       everything - partial means "skip bad input", never "skip a security check".</li>
 * </ul>
 * {@link #DEFAULT} means {@link #ALL_OR_NOTHING}. The same enum exists in the app module -
 * see {@link RestlessDenial} for why.
 */
public enum RestlessBulkMode {
    DEFAULT,
    ALL_OR_NOTHING,
    PARTIAL
}
