package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.stereotype.Repository;
import ro.cristivoicu.springbootrestless.repository.SpecificationRepository;

@Repository
public interface DepartmentRepository extends SpecificationRepository<Department, Long> {
}
