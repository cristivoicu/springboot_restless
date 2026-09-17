package ro.cristivoicu.springbootrestless.datasource;

/**
 * Opt-in marker for an entity that should be flagged rather than removed on delete. Implement
 * this (backed by a real {@code boolean deleted} column - {@code
 * RestlessResourceHandler#excludeSoftDeleted} filters on a field literally named {@code
 * "deleted"}, not this interface's method names, since JPA {@code Specification}s build
 * criteria off the entity's own persistent field) and pair it with {@link
 * ro.cristivoicu.springbootrestless.datasource.defaults.DefaultSoftDeleteDataSource} (point
 * {@code @RestlessEntity(deleteDataSource = ...)} or a hand-wired resource's {@code
 * getDeleteDataSource()} at it) to get flag-and-keep delete semantics instead of {@link
 * ro.cristivoicu.springbootrestless.datasource.defaults.DefaultDeleteDataSource}'s row removal.
 * <p>
 * Once an entity implements this, {@code RestlessResourceHandler} automatically excludes
 * soft-deleted rows from {@code findList}/{@code findPage*}/custom-read results - see {@code
 * excludeSoftDeleted}'s own javadoc for why fetch-by-id ({@code findOne}/{@code update}/{@code
 * patch}) deliberately still returns them.
 */
public interface SoftDeletable {

    boolean isDeleted();

    void setDeleted(boolean deleted);
}
