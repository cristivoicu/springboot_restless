package ro.cristivoicu.springbootrestless.controller.create;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.CreateModel;

import java.lang.reflect.Field;
import java.net.URI;

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
public abstract class CreateController<E, K, C extends CreateModel, R> implements ro.cristivoicu.springbootrestless.error.RestlessErrorScope {
    protected final CreateDataSource<E, K, C> dataSource;

    protected CreateController(CreateDataSource<E, K, C> dataSource) {
        this.dataSource = dataSource;
    }

    protected abstract Mapper<E, R> getEntityMapper();

    // 201 + Location, not 200 - RFC 9110 §15.3.2, same fix (and same reasoning) as
    // RestlessResourceHandler#create's dynamic-mechanism counterpart; this hand-subclassed tier
    // matches it on purpose (parity with the dynamic mechanism, not just within this tier).
    @PostMapping
    public ResponseEntity<R> create(@Validated @RequestBody C createDto, HttpServletRequest request) throws Exception {
        if (getEntityMapper() == null) throw new RuntimeException("Entity mapper is null");
        E created = dataSource.create(createDto);
        R body = getEntityMapper().map(created);
        Object id = idOf(created);
        if (id != null) {
            URI location = ServletUriComponentsBuilder.fromRequest(request).path("/{id}").buildAndExpand(id).toUri();
            return ResponseEntity.created(location).body(body);
        }
        return ResponseEntity.ok(body);
    }

    /** Same reflection-based {@code @Id} walk as {@code RestlessResourceHandler}'s own private copy - see its javadoc. */
    private static Object idOf(Object entity) {
        if (entity == null) {
            return null;
        }
        for (Class<?> type = entity.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    field.setAccessible(true);
                    try {
                        return field.get(entity);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Could not read @Id field of " + entity.getClass(), e);
                    }
                }
            }
        }
        return null;
    }
}
