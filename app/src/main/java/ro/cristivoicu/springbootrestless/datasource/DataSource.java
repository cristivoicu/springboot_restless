package ro.cristivoicu.springbootrestless.datasource;

import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

public abstract class DataSource<E,K> {
    protected final SpecificationRepository<E, K> specificationRepository;

    protected DataSource(SpecificationRepository<E, K> specificationRepository) {
        this.specificationRepository = specificationRepository;
    }

    /**
     * Forces a pending write to actually hit the database before {@code RestlessResourceHandler}
     * maps the result - see its own atomic-write-pipeline javadoc for why. A plain {@code
     * save()} doesn't force this on its own: a {@code @Version} bump or a database-generated
     * value some other column depends on only actually lands in the entity's in-memory fields at
     * flush time, which would otherwise still be stale by the time {@code Mapper.map(...)} reads
     * them. Public, not protected - {@code RestlessResourceHandler} (a different package) calls
     * this by composition on whichever {@code *DataSource} it's holding, the same reasoning
     * every verb method here already documents. Concrete (not abstract): {@code
     * specificationRepository} (a plain {@link org.springframework.data.jpa.repository.JpaRepository})
     * already has a {@code flush()} of its own - nothing for a subclass to override.
     */
    public final void flush() {
        specificationRepository.flush();
    }
}
