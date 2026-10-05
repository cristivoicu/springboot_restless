package ro.cristivoicu.springbootrestless.controller.read;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.PageableResponse;
import ro.cristivoicu.springbootrestless.models.SearchDto;

import java.util.List;

/**
 * {@code R} (the response DTO type every mapper below maps onto) is a real type parameter, not
 * {@code ?} - same reasoning as {@code CreateController}'s own javadoc, so springdoc can resolve
 * a concrete {@code $ref} for every response here too. One {@code R} for all three
 * projections (entity/overview/select) - every current hand-written subclass (the only kind that
 * still extends this class directly; generated resources are documented separately by {@code
 * RestlessOpenApiCustomizer}) already uses the same mapper for all three, so this costs no real
 * flexibility today. A hand-written subclass that genuinely needs three different projection
 * types doesn't fit this shared base at all - same "known simplification" the generated tier's
 * own {@code RestlessOpenApiCustomizer} javadoc already accepts for exactly this reason.
 */
public abstract class ReadController<E,K,S extends SearchDto,R> implements ro.cristivoicu.springbootrestless.error.RestlessErrorScope {
    protected final ReadDataSource<E,K,S> dataSource;

    protected abstract Specification<E> getSpecification(S searchDto);

    protected abstract Mapper<E, R> getSelectMapper();

    protected abstract Mapper<E, R> getOverviewMapper();

    protected abstract Mapper<E, R> getEntityMapper();


    protected ReadController(ReadDataSource<E, K, S> dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("{id}")
    public ResponseEntity<R> getById(@PathVariable K id) {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        var data = dataSource.findOne(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(getEntityMapper().map(data));
    }

    @GetMapping("/list")
    public ResponseEntity<List<R>> getAllAsList(S searchDto) {
        if (getOverviewMapper() == null) throw new RuntimeException("Overview mapper is null");
        return ResponseEntity.ok().body(
                getOverviewMapper().map(dataSource.findAll(this.getSpecification(searchDto)))
        );
    }

    @GetMapping()
    public ResponseEntity<PageableResponse<List<R>>> getAll(S searchDto) {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");

        return ResponseEntity.ok().body(getPaginatedData(searchDto, getEntityMapper()));
    }

    @GetMapping("/overview")
    public ResponseEntity<PageableResponse<List<R>>> getAllOverview(S searchDto) {
        if (getOverviewMapper() == null) throw new RuntimeException("Overview mapper is null");

        return ResponseEntity.ok().body(getPaginatedData(searchDto, getOverviewMapper()));
    }

    @GetMapping("/select/async")
    public ResponseEntity<PageableResponse<List<R>>> getAllForSelect(S searchDto) {
        if (getSelectMapper() == null) throw new RuntimeException("Select mapper is null");
        return ResponseEntity.ok().body(getPaginatedData(searchDto, getSelectMapper()));
    }

    private PageableResponse<List<R>> getPaginatedData(S searchDto, Mapper<E, R> mapper) {
        var data = dataSource.findAll(this.getSpecification(searchDto), searchDto.getPageable());
        PageableResponse<List<R>> response = new PageableResponse<>();
        response.setPageSize(data.getSize());
        response.setTotalPages(data.getTotalPages());
        response.setTotalElements(data.getTotalElements());
        response.setBody(mapper.map(data.getContent()));

        return response;
    }
}
