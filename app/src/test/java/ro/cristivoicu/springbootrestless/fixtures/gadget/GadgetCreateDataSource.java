package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;

@Component
public class GadgetCreateDataSource extends CreateDataSource<Gadget, Long, GadgetCreateModel> {

    public GadgetCreateDataSource(GadgetRepository repository) {
        super(repository);
    }

    @Override
    public Gadget create(GadgetCreateModel createDto) {
        Gadget gadget = new Gadget();
        gadget.setFirstName(createDto.getFirstName());
        gadget.setLastName(createDto.getLastName());
        gadget.setEmail(createDto.getEmail());
        return specificationRepository.save(gadget);
    }
}
