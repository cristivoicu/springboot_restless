package ro.cristivoicu.springbootrestless.entity.employee;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface EmployeeRepository extends SpecificationRepository<Employee, Long> {
}
