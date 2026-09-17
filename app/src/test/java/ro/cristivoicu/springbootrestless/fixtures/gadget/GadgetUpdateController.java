package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.update.UpdateController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/gadgets")
public class GadgetUpdateController extends UpdateController<Gadget, Long, GadgetUpdateModel, GadgetDto> {

    private final GadgetMapper mapper;

    public GadgetUpdateController(GadgetUpdateDataSource dataSource, GadgetMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Mapper<Gadget, GadgetDto> getEntityMapper() {
        return mapper;
    }
}
