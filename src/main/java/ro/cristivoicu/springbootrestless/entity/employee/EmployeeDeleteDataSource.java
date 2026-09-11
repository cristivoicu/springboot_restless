package ro.cristivoicu.springbootrestless.entity.employee;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;

@Component
public class EmployeeDeleteDataSource extends DeleteDataSource<Employee, Long, EmployeeDeleteModel> {

    protected EmployeeDeleteDataSource(EmployeeRepository repository) {
        super(repository);
    }

    @Override
    public void deleteById(Long id) {
        specificationRepository.deleteById(id);
    }

    @Override
    public void deleteAll(EmployeeDeleteModel d) {
        // deleteAllByIdInBatch issues a bulk JPQL delete that bypasses the persistence
        // context, so already-loaded managed instances keep resolving as present within
        // the same transaction/session. Deleting through findById+delete keeps the
        // first-level cache consistent with the database.
        d.getIds().forEach(id -> specificationRepository.findById(Long.valueOf(id))
                .ifPresent(specificationRepository::delete));
    }
}
