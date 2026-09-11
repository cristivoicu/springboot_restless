package ro.cristivoicu.springbootrestless.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Specification Repository to access a database for CRUD operations.
 *
 * @param <E> is the Entity
 * @param <K> is the primary key
 */
public interface SpecificationRepository<E, K> extends JpaRepository<E, K>, JpaSpecificationExecutor<E> {
}
