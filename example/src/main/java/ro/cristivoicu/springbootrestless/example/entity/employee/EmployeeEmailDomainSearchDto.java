package ro.cristivoicu.springbootrestless.example.entity.employee;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

/**
 * Search DTO for the {@code byEmailDomain} custom read action — deliberately something the
 * default equality-match filter can't express (a suffix {@code LIKE}), to justify the custom
 * read action mechanism existing at all.
 */
@Getter
@Setter
public class EmployeeEmailDomainSearchDto extends AbstractSearchDto {

    private String domain;
}
