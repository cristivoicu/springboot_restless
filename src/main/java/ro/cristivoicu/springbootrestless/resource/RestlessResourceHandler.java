package ro.cristivoicu.springbootrestless.resource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.core.JacksonException;
import ro.cristivoicu.springbootrestless.controller.create.CreateDataSource;
import ro.cristivoicu.springbootrestless.controller.delete.DeleteDataSource;
import ro.cristivoicu.springbootrestless.controller.read.ReadDataSource;
import ro.cristivoicu.springbootrestless.controller.update.UpdateDataSource;
import ro.cristivoicu.springbootrestless.mapper.Mapper;
import ro.cristivoicu.springbootrestless.models.CreateModel;
import ro.cristivoicu.springbootrestless.models.DeleteModel;
import ro.cristivoicu.springbootrestless.models.PageableResponse;
import ro.cristivoicu.springbootrestless.models.SearchDto;
import ro.cristivoicu.springbootrestless.models.UpdateModel;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Runtime-registered replacement for the four hand-subclassed {@code *Controller} classes.
 * <p>
 * Composes (does not replace) the entity's existing {@code CreateDataSource}/{@code ReadDataSource}/
 * {@code UpdateDataSource}/{@code DeleteDataSource} plus its {@link Mapper}s and {@link Specification}
 * builder — those stay hand-written and type-checked per aggregate, per the DDD boundary. What's new
 * here is only the HTTP wiring: one concrete, final handler method per route, shared (same {@link Method}
 * object) across every resource instance, dispatched by ordinary polymorphism once
 * {@code RestlessRegistrar} registers {@code (RequestMappingInfo, thisResourceBean, thatMethod)}.
 * <p>
 * Because DTO/id types aren't threaded through this class's own generics (deliberately — see the
 * implementation plan), request bodies/params are parsed against {@link ResourceMetadata}'s resolved
 * {@link Class} tokens at runtime, reusing Spring's own body/bind/validate infrastructure rather than
 * hand-rolling it.
 */
public abstract class RestlessResourceHandler<E, K> {

    private static final Method VALIDATION_TARGET_METHOD;

    static {
        try {
            VALIDATION_TARGET_METHOD = RestlessResourceHandler.class.getDeclaredMethod("validationTarget", Object.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Never invoked - exists purely so MethodArgumentNotValidException has a MethodParameter
    // to describe "the request body" with, matching what @Valid @RequestBody produces normally.
    @SuppressWarnings("unused")
    private void validationTarget(Object body) {
    }

    private ResourceMetadata metadata;
    private ObjectMapper objectMapper;
    private ConversionService conversionService;
    private Validator validator;
    private java.lang.reflect.Constructor<?> searchDtoConstructor;

    /**
     * Wires this resource's infra collaborators. Called once by whichever registrar discovered
     * this bean (a hardcoded call in Stage 1, {@code RestlessRegistrar} from Stage 2 on) — kept
     * separate from the constructor so the entity author's subclass stays free of infra plumbing.
     */
    public final void init(ResourceMetadata metadata, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator) {
        this.metadata = metadata;
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.validator = validator;
        try {
            this.searchDtoConstructor = metadata.searchDtoType().getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(metadata.searchDtoType() + " needs a no-arg constructor to be bound from query parameters", e);
        }
    }

    public final ResourceMetadata getMetadata() {
        return metadata;
    }

    /**
     * Resolves this resource's entity/id/DTO {@link Class} tokens via reflection off the
     * concrete subclass's own generics — {@code E}/{@code K} from this class's superclass
     * type arguments, each DTO type from the corresponding {@code *DataSource} subclass's
     * own generics. Called once by {@code RestlessRegistrar} at startup; kept here (not in
     * the registrar) because only code inside this class can call the protected
     * {@code getXDataSource()} accessors.
     */
    public final ResourceMetadata resolveMetadata(String basePath) {
        Class<?>[] entityAndId = GenericTypeResolver.resolveTypeArguments(getClass(), RestlessResourceHandler.class);
        if (entityAndId == null) {
            throw new IllegalStateException(getClass() + " must extend RestlessResourceHandler<E, K> with concrete type arguments");
        }

        Class<?> createModelType = GenericTypeResolver.resolveTypeArguments(getCreateDataSource().getClass(), CreateDataSource.class)[2];
        Class<?> updateModelType = GenericTypeResolver.resolveTypeArguments(getUpdateDataSource().getClass(), UpdateDataSource.class)[2];
        Class<?> deleteModelType = GenericTypeResolver.resolveTypeArguments(getDeleteDataSource().getClass(), DeleteDataSource.class)[2];
        Class<?> searchDtoType = GenericTypeResolver.resolveTypeArguments(getReadDataSource().getClass(), ReadDataSource.class)[2];

        return new ResourceMetadata(basePath, entityAndId[0], entityAndId[1],
                createModelType, updateModelType, deleteModelType, searchDtoType);
    }

    protected abstract CreateDataSource<E, K, ?> getCreateDataSource();

    protected abstract ReadDataSource<E, K, ?> getReadDataSource();

    protected abstract UpdateDataSource<E, K, ?> getUpdateDataSource();

    protected abstract DeleteDataSource<E, K, ?> getDeleteDataSource();

    protected abstract Mapper<E, ?> getEntityMapper();

    protected abstract Mapper<E, ?> getOverviewMapper();

    protected abstract Mapper<E, ?> getSelectMapper();

    protected abstract Specification<E> getSpecification(SearchDto searchDto);

    // ---- shared handler methods: one Method object per route, inherited by every subclass ----

    public final ResponseEntity<?> create(HttpServletRequest request) throws Exception {
        Object body = readBody(request, metadata.createModelType());
        validate(body);
        // Raw-type escape hatch: C's bound (CreateModel) can't be named here since the actual
        // type is only known at runtime via metadata.createModelType(). Safe because `body` was
        // just deserialized as exactly that class.
        @SuppressWarnings({"unchecked", "rawtypes"})
        CreateDataSource rawDataSource = getCreateDataSource();
        @SuppressWarnings("unchecked")
        E created = (E) rawDataSource.create((CreateModel) body);
        return ResponseEntity.ok(getEntityMapper().map(created));
    }

    public final ResponseEntity<?> findOne(HttpServletRequest request) {
        K id = extractId(request);
        E found = getReadDataSource().findOne(id);
        if (found == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(getEntityMapper().map(found));
    }

    public final ResponseEntity<List<?>> findList(HttpServletRequest request) throws Exception {
        SearchDto searchDto = bindSearchDto(request);
        Specification<E> spec = getSpecification(searchDto);
        List<E> data = getReadDataSource().findAll(spec);
        return ResponseEntity.ok(getOverviewMapper().map(data));
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPage(HttpServletRequest request) throws Exception {
        return ResponseEntity.ok(paginate(request, getEntityMapper()));
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPageOverview(HttpServletRequest request) throws Exception {
        return ResponseEntity.ok(paginate(request, getOverviewMapper()));
    }

    public final ResponseEntity<PageableResponse<List<?>>> findPageSelect(HttpServletRequest request) throws Exception {
        return ResponseEntity.ok(paginate(request, getSelectMapper()));
    }

    public final ResponseEntity<?> update(HttpServletRequest request) throws Exception {
        K id = extractId(request);
        Object body = readBody(request, metadata.updateModelType());
        validate(body);
        @SuppressWarnings({"unchecked", "rawtypes"})
        UpdateDataSource rawDataSource = getUpdateDataSource();
        @SuppressWarnings("unchecked")
        E updated = (E) rawDataSource.update(id, (UpdateModel) body);
        return ResponseEntity.ok(getEntityMapper().map(updated));
    }

    public final ResponseEntity<?> deleteById(HttpServletRequest request) {
        K id = extractId(request);
        getDeleteDataSource().deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public final ResponseEntity<?> deleteAll(HttpServletRequest request) throws Exception {
        Object body = readBody(request, metadata.deleteModelType());
        @SuppressWarnings({"unchecked", "rawtypes"})
        DeleteDataSource rawDataSource = getDeleteDataSource();
        rawDataSource.deleteAll((DeleteModel) body);
        return ResponseEntity.noContent().build();
    }

    // ---- shared per-request parsing, reusing Spring's own infra rather than hand-rolling it ----

    private PageableResponse<List<?>> paginate(HttpServletRequest request, Mapper<E, ?> mapper) throws Exception {
        SearchDto searchDto = bindSearchDto(request);
        Specification<E> spec = getSpecification(searchDto);
        Page<E> page = getReadDataSource().findAll(spec, searchDto.getPageable());

        PageableResponse<List<?>> response = new PageableResponse<>();
        response.setPageSize(page.getSize());
        response.setTotalPages(page.getTotalPages());
        response.setTotalElements(page.getTotalElements());
        response.setBody(mapper.map(page.getContent()));
        return response;
    }

    private Object readBody(HttpServletRequest request, Class<?> type) throws java.io.IOException {
        // Bypasses HttpMessageConverter (there's no typed @RequestBody parameter to hang one off
        // of), so malformed JSON must be translated to 400 by hand - otherwise it surfaces as an
        // unhandled JacksonException (500) where the hand-written routes get
        // HttpMessageNotReadableException (400) for free from the framework.
        try {
            return objectMapper.readValue(request.getInputStream(), type);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body", e);
        }
    }

    private SearchDto bindSearchDto(HttpServletRequest request) throws Exception {
        SearchDto searchDto = (SearchDto) searchDtoConstructor.newInstance();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(searchDto);
        binder.setConversionService(conversionService);
        binder.bind(request);
        return searchDto;
    }

    @SuppressWarnings("unchecked")
    private K extractId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String rawId = ((Map<String, String>) attribute).get("id");
        // Bypasses the normal @PathVariable argument resolver, so a malformed id (e.g. "abc" for
        // a Long) must be translated to 400 by hand here - otherwise it surfaces as an unhandled
        // ConversionException (500) where the hand-written routes get
        // MethodArgumentTypeMismatchException (400) for free from the framework.
        try {
            return conversionService.convert(rawId, (Class<K>) metadata.idType());
        } catch (ConversionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to convert id '" + rawId + "' to " + metadata.idType().getSimpleName(), e);
        }
    }

    private void validate(Object target) throws MethodArgumentNotValidException {
        BindingResult errors = new BeanPropertyBindingResult(target, target.getClass().getSimpleName());
        validator.validate(target, errors);
        if (errors.hasErrors()) {
            throw new MethodArgumentNotValidException(new MethodParameter(VALIDATION_TARGET_METHOD, 0), errors);
        }
    }
}
