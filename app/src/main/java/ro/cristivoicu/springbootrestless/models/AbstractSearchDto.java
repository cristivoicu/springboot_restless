package ro.cristivoicu.springbootrestless.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base for {@link SearchDto} implementations: carries the usual
 * page/size/sort query parameters and builds a {@link Pageable} from them,
 * so concrete search DTOs only need to declare their own filter fields.
 * <p>
 * {@code sort} is repeatable ({@code ?sort=name,asc&sort=id,desc}) and each entry is also valid
 * on its own in a single occurrence ({@code ?sort=name,asc}) - each entry is {@code "property"}
 * (ascending), {@code "property,direction"}, or {@code "-property"} (descending, a leading-dash
 * shorthand GitHub's and many other REST APIs' own sort parameters already use). Direction
 * defaults to {@code asc} - the same convention Spring Data's own {@code Pageable} argument
 * resolver uses, chosen so it's already familiar rather than inventing a new one. Empty (the
 * default) sorts by {@code id} ascending, matching this class's behavior before multi-field sort
 * existed. Property names aren't validated here - {@code RestlessResourceHandler} does that once
 * it knows the entity type they're being applied to, translating an unknown one into a 400 rather
 * than letting Hibernate turn it into an opaque 500.
 * <p>
 * {@code RestlessResourceHandler#bindSortDirectly} reads the request's raw {@code sort}
 * parameter values itself rather than leaving {@code sort} to the generic {@code
 * ServletRequestDataBinder}/{@code ConversionService} path every other field here goes through -
 * a single occurrence otherwise gets silently comma-split by {@code
 * DefaultConversionService}'s {@code StringToCollectionConverter} before it ever reaches {@link
 * #getPageable()}, turning one {@code "name,asc"} entry into two broken ones, {@code "name"} and
 * {@code "asc"}. See its own javadoc for the full mechanism.
 */
@Getter
@Setter
public abstract class AbstractSearchDto implements SearchDto {

    protected int page = 0;
    protected int size = 20;
    protected List<String> sort = new ArrayList<>();

    /** {@code @Schema(hidden)} - a derived convenience, not a real request field; without this it leaks Spring Data's own {@link Pageable} shape (pageNumber/pageSize/unpaged/offset/...) into every search DTO's schema, duplicating {@link #page}/{@link #size}/{@link #sort} under a confusing "pageable" property. */
    @Override
    @Schema(hidden = true)
    public Pageable getPageable() {
        return PageRequest.of(page, size, sort.isEmpty()
                ? Sort.by(Sort.Direction.ASC, "id")
                : Sort.by(sort.stream().map(AbstractSearchDto::parseOrder).toList()));
    }

    /**
     * {@code "property"} (ascending), {@code "property,direction"}, or a leading {@code -}
     * ({@code "-property"}, descending) - the same shorthand GitHub's and many other REST APIs'
     * own {@code sort}/{@code order_by} parameters already use. The comma form takes precedence
     * if a clause somehow carries both (it never will in practice - nothing generates one).
     */
    private static Sort.Order parseOrder(String clause) {
        String trimmed = clause.trim();
        int comma = trimmed.indexOf(',');
        if (comma >= 0) {
            String property = trimmed.substring(0, comma).trim();
            Sort.Direction direction = Sort.Direction.fromString(trimmed.substring(comma + 1).trim());
            return new Sort.Order(direction, property);
        }
        if (trimmed.startsWith("-")) {
            return new Sort.Order(Sort.Direction.DESC, trimmed.substring(1).trim());
        }
        return new Sort.Order(Sort.Direction.ASC, trimmed);
    }
}
