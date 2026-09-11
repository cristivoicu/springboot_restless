package ro.cristivoicu.springbootrestless.models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Convenience base for {@link SearchDto} implementations: carries the usual
 * page/size/sort query parameters and builds a {@link Pageable} from them,
 * so concrete search DTOs only need to declare their own filter fields.
 */
@Getter
@Setter
public abstract class AbstractSearchDto implements SearchDto {

    protected int page = 0;
    protected int size = 20;
    protected String sortBy = "id";
    protected Sort.Direction sortDirection = Sort.Direction.ASC;

    @Override
    public Pageable getPageable() {
        return PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
    }
}
