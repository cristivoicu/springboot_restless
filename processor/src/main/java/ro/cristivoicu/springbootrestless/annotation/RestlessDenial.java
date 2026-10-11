package ro.cristivoicu.springbootrestless.annotation;

/**
 * What a caller who isn't allowed to see or act on something gets back.
 * <p>
 * {@link #FORBIDDEN} (403) says "this exists, but not for you" - clear for an internal tool, and the
 * historical default. {@link #NOT_FOUND} (404) is indistinguishable from an id that never existed,
 * so ids of other people's rows can't be probed - the right answer for anything holding user data.
 * <p>
 * {@link #DEFAULT} on {@code @RestlessResource}/{@code @RestlessEntity} means "use
 * {@code restless.authorization.deny-as}", which itself defaults to {@link #FORBIDDEN}.
 * <p>
 * The same enum exists in the app module (runtime {@code @RestlessResource}) - same
 * precedent as {@link RestlessParent}: this jar is never on the runtime classpath.
 */
public enum RestlessDenial {
    DEFAULT,
    FORBIDDEN,
    NOT_FOUND
}
