package ro.cristivoicu.springbootrestless.entity.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;

import java.util.List;

@Component
public class EmployeeReadDataSource extends ReadDataSource<Employee, Long, EmployeeSearchDto> {

    public EmployeeReadDataSource(EmployeeRepository repository) {
        super(repository);
    }

    @Override
    public Page<Employee> findAll(Specification<Employee> specification, Pageable pageable) {
        return specificationRepository.findAll(specification, pageable);
    }

    @Override
    public List<Employee> findAll(Specification<Employee> specification) {
        return specificationRepository.findAll(specification);
    }

    @Override
    public Employee findOne(Specification<Employee> specification, Long id) {
        return specificationRepository.findOne(specification)
                .filter(employee -> id.equals(employee.getId()))
                .orElse(null);
    }

    @Override
    public Employee findOne(Long id) {
        return specificationRepository.findById(id).orElse(null);
    }
}
