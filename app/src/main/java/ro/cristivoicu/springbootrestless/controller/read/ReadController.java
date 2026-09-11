package ro.cristivoicu.springbootrestless.controller.read;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.PageableResponse;
import ro.cristivoicu.springbootrestless.models.SearchDto;

import java.util.List;

public abstract class ReadController<E,K,R extends SearchDto> {
    protected final ReadDataSource<E,K,R> dataSource;

    protected abstract Specification<E> getSpecification(R searchDto);

    protected abstract Mapper<E, ?> getSelectMapper();

    protected abstract Mapper<E, ?> getOverviewMapper();

    protected abstract Mapper<E, ?> getEntityMapper();


    protected ReadController(ReadDataSource<E, K, R> dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("{id}")
    public ResponseEntity<?> getById(@PathVariable K id) {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        var data = dataSource.findOne(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(getEntityMapper().map(data));
    }

    @GetMapping("/list")
    public ResponseEntity<List<?>> getAllAsList(R searchDto) {
        if (getOverviewMapper() == null) throw new RuntimeException("Overview mapper is null");
        return ResponseEntity.ok().body(
                getOverviewMapper().map(dataSource.findAll(this.getSpecification(searchDto)))
        );
    }

    @GetMapping()
    public ResponseEntity<PageableResponse<List<?>>> getAll(R searchDto) {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");

        return ResponseEntity.ok().body(getPaginatedData(searchDto, getEntityMapper()));
    }

    @GetMapping("/overview")
    public ResponseEntity<PageableResponse<List<?>>> getAllOverview(R searchDto) {
        if (getOverviewMapper() == null) throw new RuntimeException("Overview mapper is null");

        return ResponseEntity.ok().body(getPaginatedData(searchDto, getOverviewMapper()));
    }

    @GetMapping("/select/async")
    public ResponseEntity<PageableResponse<List<?>>> getAllForSelect(R searchDto) {
        if (getSelectMapper() == null) throw new RuntimeException("Select mapper is null");
        return ResponseEntity.ok().body(getPaginatedData(searchDto, getSelectMapper()));
    }

    private PageableResponse<List<?>> getPaginatedData(R searchDto, Mapper<E, ?> mapper) {
        var data = dataSource.findAll(this.getSpecification(searchDto), searchDto.getPageable());
        PageableResponse<List<?>> response = new PageableResponse<>();
        response.setPageSize(data.getSize());
        response.setTotalPages(data.getTotalPages());
        response.setTotalElements(data.getTotalElements());
        response.setBody(mapper.map(data.getContent()));

        return response;
    }
}
