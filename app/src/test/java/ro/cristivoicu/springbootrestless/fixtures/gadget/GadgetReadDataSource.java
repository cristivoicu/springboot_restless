package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;

import java.util.List;

@Component
public class GadgetReadDataSource extends ReadDataSource<Gadget, Long, GadgetSearchDto> {

    public GadgetReadDataSource(GadgetRepository repository) {
        super(repository);
    }

    @Override
    public Page<Gadget> findAll(Specification<Gadget> specification, Pageable pageable) {
        return specificationRepository.findAll(specification, pageable);
    }

    @Override
    public List<Gadget> findAll(Specification<Gadget> specification) {
        return specificationRepository.findAll(specification);
    }

    @Override
    public Gadget findOne(Specification<Gadget> specification, Long id) {
        return specificationRepository.findOne(specification)
                .filter(gadget -> id.equals(gadget.getId()))
                .orElse(null);
    }

    @Override
    public Gadget findOne(Long id) {
        return specificationRepository.findById(id).orElse(null);
    }
}
