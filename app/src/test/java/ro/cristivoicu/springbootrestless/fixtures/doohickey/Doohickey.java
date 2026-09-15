package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;

/**
 * Test-only fixture (not shipped): the "everything generated" tier, one step past {@link
 * ro.cristivoicu.springbootrestless.fixtures.sprocket.Sprocket}'s escape-hatch-{@code
 * createDataSource}-only proof. No hand-written {@code DoohickeyMapper} exists at all -
 * {@code RestlessEntityProcessor} generates a reflective, {@code BeanUtils}-based one onto the
 * hand-written {@link DoohickeyDto}, skipping whichever of its fields are marked {@code
 * @RestlessMapperExclude} (see {@link DoohickeyDto#getSecret()}). No hand-written resource bean
 * exists either - {@code authorizationGuard} below wires {@link DoohickeyAuthorizationGuard} into
 * the generated resource exactly like a hand-written one overriding {@code
 * getAuthorizationGuard()} would, instead of inheriting {@code RestlessResourceHandler}'s
 * default-permissive {@code AuthorizationGuard.allowAll()}. {@code version = "1"} also proves
 * {@code @RestlessEntity}'s API-versioning attribute reaches the generated {@code
 * @RestlessResource} and, through it, every route {@code RestlessRegistrar} registers - see
 * {@code DynamicRouteVersionTest}. {@code patchDataSource} wires in {@link
 * DoohickeyPatchDataSource}, adding a {@code PATCH} route the generated resource otherwise
 * wouldn't have at all - see {@code DoohickeyPatchTest}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RestlessEntity(basePath = "/doohickeys", authorizationGuard = DoohickeyAuthorizationGuard.class,
        patchDataSource = DoohickeyPatchDataSource.class, version = "1")
public class Doohickey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String secret;
}
