package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;

@Component
public class GadgetUpdateDataSource extends UpdateDataSource<Gadget, Long, GadgetUpdateModel> {

    public GadgetUpdateDataSource(GadgetRepository repository) {
        super(repository);
    }

    @Override
    public Gadget update(Long id, GadgetUpdateModel updateDto) {
        Gadget gadget = specificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gadget " + id + " not found"));
        gadget.setFirstName(updateDto.getFirstName());
        gadget.setLastName(updateDto.getLastName());
        gadget.setEmail(updateDto.getEmail());
        return specificationRepository.save(gadget);
    }
}
