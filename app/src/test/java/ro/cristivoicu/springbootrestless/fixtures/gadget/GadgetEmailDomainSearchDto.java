package ro.cristivoicu.springbootrestless.fixtures.gadget;

import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

/**
 * Search DTO for the {@code byEmailDomain} custom read action - deliberately something the
 * default equality-match filter can't express (a suffix {@code LIKE}).
 */
@Getter
@Setter
public class GadgetEmailDomainSearchDto extends AbstractSearchDto {

    private String domain;
}
