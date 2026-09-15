package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessMapperExclude;
import ro.cristivoicu.springbootrestless.models.EntityDto;

/**
 * Hand-written on purpose - response *shape* always is, generated mapper or not (see {@code
 * Mapper}'s javadoc). No hand-written {@code DoohickeyMapper} exists, so {@code
 * RestlessEntityProcessor} generates one reflectively onto this class; {@link #getSecret()} is
 * marked {@link RestlessMapperExclude} so that generated mapper always leaves it {@code null}
 * instead of blindly copying {@link Doohickey#getSecret()} - proven by {@code
 * DoohickeyGeneratedDefaultsTest}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoohickeyDto implements EntityDto {
    private Long id;
    private String name;

    @RestlessMapperExclude
    private String secret;
}
