package ro.cristivoicu.springbootrestless.controller.create;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.CreateModel;

/**
 * {@code R} (the response DTO type {@link #getEntityMapper} maps onto) is a real type parameter,
 * not a {@code ?} wildcard, specifically so springdoc's own generic-superclass resolution - which
 * already resolves {@code C} into a concrete {@code $ref} for the request body, see {@code
 * RestlessOpenApiCustomizer}'s javadoc for why the *dynamically registered* routes need their own
 * separate documentation path - can do the same for {@code create}'s response. A concrete
 * subclass like {@code EmployeeCreateController extends CreateController<Employee, Long,
 * EmployeeCreateModel, EmployeeDto>} fixes {@code R} to a real DTO class, and springdoc's
 * reflection sees exactly that, no different from any hand-written {@code
 * ResponseEntity<EmployeeDto>} return type would document.
 */
public abstract class CreateController<E, K, C extends CreateModel, R> {
    protected final CreateDataSource<E, K, C> dataSource;

    protected CreateController(CreateDataSource<E, K, C> dataSource) {
        this.dataSource = dataSource;
    }

    protected abstract Mapper<E, R> getEntityMapper();

    @PostMapping
    public ResponseEntity<R> create(@Validated @RequestBody C createDto) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        return ResponseEntity.ok().body(
                getEntityMapper().map(dataSource.create(createDto))
        );
    }

}
