package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.create.CreateController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/gadgets")
public class GadgetCreateController extends CreateController<Gadget, Long, GadgetCreateModel, GadgetDto> {

    private final GadgetMapper mapper;

    public GadgetCreateController(GadgetCreateDataSource dataSource, GadgetMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Mapper<Gadget, GadgetDto> getEntityMapper() {
        return mapper;
    }
}
