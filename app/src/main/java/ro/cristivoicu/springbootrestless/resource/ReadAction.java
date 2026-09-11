package ro.cristivoicu.springbootrestless.resource;

import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.models.SearchDto;

/**
 * A named custom read action: its own {@link SearchDto} subtype and its own query-building
 * logic, for anything the default equality-match filter
 * ({@link RestlessResourceHandler#getSpecification}) can't express (ranges, {@code LIKE},
 * cross-field logic, ...). Declared via {@link RestlessResourceHandler#getCustomReadActions()}
 * and exposed as an extra {@code GET {basePath}/actions/{name}} route by {@code RestlessRegistrar}.
 *
 * @param <E> the entity type
 * @param <R> this action's search DTO type
 */
public interface ReadAction<E, R extends SearchDto> {

    Class<R> getSearchDtoType();

    Specification<E> buildSpecification(R searchDto);
}
