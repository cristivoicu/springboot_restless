package ro.cristivoicu.springbootrestless.annotation;

/**
 * Compile-time mirror of {@code AuthorizationGuard.Action}'s nine fixed-route values (excluding
 * {@code PATCH}/{@code CUSTOM_READ}, which aren't part of {@code
 * RestlessResourceHandler#getEnabledOperations} at all - see its javadoc) - a separate enum, not
 * a reference to {@code Action} itself, because this module ({@code processor}) deliberately
 * carries no dependency on {@code app} at all (see its pom.xml; the two can't depend on each
 * other, since {@code app} needs {@code processor} at build time). {@code
 * RestlessEntityProcessor} translates each constant here to the identically-named {@code Action}
 * constant by string in the code it generates, rather than by any shared type - the names have to
 * stay in sync by convention, the same way this enum's own value set has to stay in sync with
 * {@code Action}'s if either one ever changes.
 */
public enum RestlessOperation {
    CREATE, READ_ONE, READ_LIST, READ_PAGE, READ_PAGE_OVERVIEW, READ_PAGE_SELECT,
    UPDATE, DELETE_ONE, DELETE_ALL
}
