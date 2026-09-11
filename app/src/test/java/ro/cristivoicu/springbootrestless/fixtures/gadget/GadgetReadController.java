package ro.cristivoicu.springbootrestless.fixtures.gadget;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cristivoicu.springbootrestless.controller.read.ReadController;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

@RestController
@RequestMapping("/gadgets")
public class GadgetReadController extends ReadController<Gadget, Long, GadgetSearchDto> {

    private final GadgetMapper mapper;

    public GadgetReadController(GadgetReadDataSource dataSource, GadgetMapper mapper) {
        super(dataSource);
        this.mapper = mapper;
    }

    @Override
    protected Specification<Gadget> getSpecification(GadgetSearchDto searchDto) {
        return (root, query, cb) -> StringUtils.hasText(searchDto.getLastName())
                ? cb.equal(root.get("lastName"), searchDto.getLastName())
                : cb.conjunction();
    }

    @Override
    protected Mapper<Gadget, ?> getSelectMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Gadget, ?> getOverviewMapper() {
        return mapper;
    }

    @Override
    protected Mapper<Gadget, ?> getEntityMapper() {
        return mapper;
    }
}
