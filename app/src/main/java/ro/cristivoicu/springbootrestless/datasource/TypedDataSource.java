package ro.cristivoicu.springbootrestless.datasource;

/**
 * Escape hatch for generic-type resolution: {@code RestlessResourceHandler.resolveMetadata()}
 * normally recovers a {@code *DataSource}'s DTO type by reflecting on the concrete subclass's
 * own generics (e.g. {@code class EmployeeCreateDataSource extends CreateDataSource<Employee,
 * Long, EmployeeCreateModel>}). A directly-instantiated generic DataSource (like the {@code
 * Default*DataSource} classes, built with {@code new DefaultCreateDataSource<>(repo, ...)}) has
 * its type arguments erased at the instance level and can't be resolved that way — it must
 * supply its DTO {@link Class} token explicitly instead.
 *
 * @param <D> the DTO type this data source operates on (a CreateModel/UpdateModel/DeleteModel)
 */
public interface TypedDataSource<D> {
    Class<D> getDtoType();
}
