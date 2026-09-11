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
    public ResponseEntity<?> getById(@PathVariable String id) throws Exception {
        var data = dataSource.getData(id);
        return ResponseEntity.ok().body(
                getEntityMapper().mapToDTO((data)
                ));
    }

    @GetMapping("/list")
    public ResponseEntity<List<?>> getAllEmployeesAsList(R searchDto) {
        if (getOverviewMapper() == null) throw new RuntimeException("Overview mapper is null");
        return ResponseEntity.ok().body(
                getOverviewMapper().mapToDTO(dataSource.getData(this.getSpecification((S) searchDto)))
        );
    }

    @GetMapping()
    public ResponseEntity<PageableResponse<List<?>>> getAllEmployees(S searchDto) {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");

        return ResponseEntity.ok().body(getPaginatedData(searchDto));
    }

    @GetMapping("/overview")
    public ResponseEntity<PageableResponse<List<?>>> getAllEmployeesOverview(S searchDto) {
        if (getOverviewMapper() == null) throw new RuntimeException("Overview mapper is null");

        return ResponseEntity.ok().body(getPaginatedData(searchDto));
    }

    @GetMapping("/select/async")
    public ResponseEntity<PageableResponse<List<?>>> getAllEmployeesForSelectDto(S searchDto) {
        if (getSelectMapper() == null) throw new RuntimeException("Select mapper is null");
        return ResponseEntity.ok().body(getPaginatedData(searchDto));
    }

    private PageableResponse<List<?>> getPaginatedData(R searchDto) {
        var data = dataSource.getData(this.getSpecification((S) searchDto), searchDto.getPageable());
        PageableResponse<List<?>> response = new PageableResponse<>();
        response.setPageSize(data.getSize());
        response.setTotalPages(data.getTotalPages());
        response.setTotalElements(data.getTotalElements());
        response.setBody(getOverviewMapper().mapToDTO(data.getContent()));

        return response;
    }
}
