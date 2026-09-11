package ro.cristivoicu.springbootrestless.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Specification Repository to access a database for CRUD operations.
 * <p>
 * Base contract only — not itself a repository. Without {@link NoRepositoryBean},
 * Spring Data's repository scan tries to instantiate this raw (unbound {@code E})
 * interface as a bean in its own right and fails with "Not a managed type: class java.lang.Object".
 *
 * @param <E> is the Entity
 * @param <K> is the primary key
 */
@NoRepositoryBean
public interface SpecificationRepository<E, K> extends JpaRepository<E, K>, JpaSpecificationExecutor<E> {
}
