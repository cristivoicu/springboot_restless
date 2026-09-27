package ro.cristivoicu.springbootrestless.fixtures.cog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;
import ro.cristivoicu.springbootrestless.annotation.RestlessOperation;

/**
 * Test-only fixture (not shipped): proves {@code @RestlessEntity(operations = ...)} on the
 * compile-time generated tier - only the read routes get registered by {@code
 * CogRestlessResource}, mirroring the read-only example in {@code
 * RestlessResourceHandler#getEnabledOperations}'s own javadoc. {@link CogCreateModel}/{@link
 * CogUpdateModel} still exist even though {@code CREATE}/{@code UPDATE} are excluded here -
 * see {@code RestlessEntity#operations}'s javadoc for why disabling an operation doesn't relax
 * the naming-convention requirement on its DTO.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/cogs", allowAll = true,
        operations = {RestlessOperation.READ_ONE, RestlessOperation.READ_LIST, RestlessOperation.READ_PAGE})
public class Cog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int teeth;
}
