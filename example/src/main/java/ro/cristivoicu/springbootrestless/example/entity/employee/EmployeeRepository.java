package ro.cristivoicu.springbootrestless.example.entity.employee;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface EmployeeRepository extends SpecificationRepository<Employee, Long> {
}
