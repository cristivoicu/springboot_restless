package ro.cristivoicu.springbootrestless.annotation;

/**
 * What a plain {@code GET {basePath}} returns.
 * <ul>
 *   <li>{@link #PAGE} (default) - a {@code PageableResponse}; the bare array is at
 *       {@code GET {basePath}/list};</li>
 *   <li>{@link #LIST} - the bare array (capped, see {@code restless.list.max-size}); pages move to
 *       {@code GET {basePath}/page}, and {@code /list} keeps working as an alias.</li>
 * </ul>
 * {@link #DEFAULT} defers to {@code restless.collection-get}. The same enum exists in the processor
 * module - see {@link RestlessDenial} for why.
 */
public enum RestlessCollectionGet {
    DEFAULT,
    PAGE,
    LIST
}
