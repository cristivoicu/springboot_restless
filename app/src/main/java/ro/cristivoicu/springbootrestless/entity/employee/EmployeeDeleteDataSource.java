package ro.cristivoicu.springbootrestless.entity.employee;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
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
        d.getIds().forEach(id -> specificationRepository.findById(parseId(id))
                .ifPresent(specificationRepository::delete));
    }

    // Ids are strings (see DeleteModel's javadoc) - converted the same way
    // RestlessResourceHandler.convertId()/DefaultDeleteDataSource do, so a malformed id 400s
    // instead of surfacing as an unhandled NumberFormatException (500).
    private static Long parseId(String id) {
        try {
            return DefaultConversionService.getSharedInstance().convert(id, Long.class);
        } catch (ConversionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to convert id '" + id + "' to Long", e);
        }
    }
}
