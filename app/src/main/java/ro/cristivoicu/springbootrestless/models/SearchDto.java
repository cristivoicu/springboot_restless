package ro.cristivoicu.springbootrestless.models;

import org.springframework.data.domain.Pageable;

/**
 * Marks that the POJO is a SearchDto - entity's searchable attributes
 */
public interface SearchDto {
    Pageable getPageable();
}
