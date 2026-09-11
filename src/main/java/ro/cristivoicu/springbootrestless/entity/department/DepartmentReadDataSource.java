package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;

import java.util.List;

@Component
public class DepartmentReadDataSource extends ReadDataSource<Department, Long, DepartmentSearchDto> {

    public DepartmentReadDataSource(DepartmentRepository repository) {
        super(repository);
    }

    @Override
    public Page<Department> findAll(Specification<Department> specification, Pageable pageable) {
        return specificationRepository.findAll(specification, pageable);
    }

    @Override
    public List<Department> findAll(Specification<Department> specification) {
        return specificationRepository.findAll(specification);
    }

    @Override
    public Department findOne(Specification<Department> specification, Long id) {
        return specificationRepository.findOne(specification)
                .filter(department -> id.equals(department.getId()))
                .orElse(null);
    }

    @Override
    public Department findOne(Long id) {
        return specificationRepository.findById(id).orElse(null);
    }
}
