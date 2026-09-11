package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;

@Component
public class GadgetDeleteDataSource extends DeleteDataSource<Gadget, Long, GadgetDeleteModel> {

    public GadgetDeleteDataSource(GadgetRepository repository) {
        super(repository);
    }

    @Override
    public void deleteById(Long id) {
        specificationRepository.deleteById(id);
    }

    @Override
    public void deleteAll(GadgetDeleteModel d) {
        // findById+delete (not a bulk JPQL delete) to keep the persistence context's
        // first-level cache consistent with the database.
        d.getIds().forEach(id -> specificationRepository.findById(parseId(id))
                .ifPresent(specificationRepository::delete));
    }

    private static Long parseId(String id) {
        try {
            return DefaultConversionService.getSharedInstance().convert(id, Long.class);
        } catch (ConversionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to convert id '" + id + "' to Long", e);
        }
    }
}
