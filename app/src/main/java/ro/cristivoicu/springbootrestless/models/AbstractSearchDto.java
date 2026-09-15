package ro.cristivoicu.springbootrestless.models;

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
 * {@code sort} is repeatable ({@code ?sort=name,asc&sort=id,desc}), each entry {@code
 * "property"} or {@code "property,direction"} (direction defaults to {@code asc}) - the same
 * convention Spring Data's own {@code Pageable} argument resolver uses, chosen so it's already
 * familiar rather than inventing a new one. Empty (the default) sorts by {@code id} ascending,
 * matching this class's behavior before multi-field sort existed. Property names aren't validated
 * here - {@code RestlessResourceHandler} does that once it knows the entity type they're being
 * applied to, translating an unknown one into a 400 rather than letting Hibernate turn it into an
 * opaque 500.
 */
@Getter
@Setter
public abstract class AbstractSearchDto implements SearchDto {

    protected int page = 0;
    protected int size = 20;
    protected List<String> sort = new ArrayList<>();

    @Override
    public Pageable getPageable() {
        return PageRequest.of(page, size, sort.isEmpty()
                ? Sort.by(Sort.Direction.ASC, "id")
                : Sort.by(sort.stream().map(AbstractSearchDto::parseOrder).toList()));
    }

    private static Sort.Order parseOrder(String clause) {
        int comma = clause.indexOf(',');
        if (comma < 0) {
            return new Sort.Order(Sort.Direction.ASC, clause.trim());
        }
        String property = clause.substring(0, comma).trim();
        Sort.Direction direction = Sort.Direction.fromString(clause.substring(comma + 1).trim());
        return new Sort.Order(direction, property);
    }
}
