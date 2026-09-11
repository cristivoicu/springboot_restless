package ro.cristivoicu.springbootrestless.entity.department;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;

@Component
public class DepartmentDeleteDataSource extends DeleteDataSource<Department, Long, DepartmentDeleteModel> {

    public DepartmentDeleteDataSource(DepartmentRepository repository) {
        super(repository);
    }

    @Override
    public void deleteById(Long id) {
        specificationRepository.deleteById(id);
    }

    @Override
    public void deleteAll(DepartmentDeleteModel d) {
        // see EmployeeDeleteDataSource for why findById+delete is used instead of a bulk
        // JPQL delete: bulk deletes bypass the persistence context's first-level cache.
        d.getIds().forEach(id -> specificationRepository.findById(id)
                .ifPresent(specificationRepository::delete));
    }
}
