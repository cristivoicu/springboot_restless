package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

import java.util.Optional;

@Repository
public interface EmployeeRepository extends SpecificationRepository<Employee, Long> {

    /**
     * Correlates the authenticated principal (JWT {@code email} claim) back to their own
     * {@link Employee} row - used by {@code ProjectAuthorizationGuardBean} to resolve "what's my
     * department" for a principal attribute Keycloak itself never issues as a claim.
     */
    Optional<Employee> findByEmail(String email);
}
