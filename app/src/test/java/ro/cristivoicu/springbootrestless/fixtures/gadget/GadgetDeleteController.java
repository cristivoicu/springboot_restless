package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/gadgets")
public class GadgetDeleteController extends DeleteController<Gadget, Long, GadgetDeleteModel> {

    private final GadgetMapper mapper;

    public GadgetDeleteController(GadgetDeleteDataSource dataSource, GadgetMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Mapper<Gadget, ?> getEntityMapper() {
        return mapper;
    }
}
