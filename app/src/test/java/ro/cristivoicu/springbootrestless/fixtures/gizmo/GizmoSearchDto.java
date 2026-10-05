package ro.cristivoicu.springbootrestless.fixtures.gizmo;

import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import ro.cristivoicu.springbootrestless.models.AbstractSearchDto;

import java.util.List;

/**
 * {@code name} is plain equality (the original field, unchanged). The five fields below exercise
 * the filter DSL's operator-suffix convention ({@code ro.cristivoicu.springbootrestless.filter.FilterOperator})
 * against {@link Gizmo}'s own fields - see {@code GizmoFilterTest}. {@code Gt}/{@code Lt} aren't
 * separately exercised here (same predicate-building shape as {@code Gte}/{@code Lte}, a
 * deliberate, documented scope trim - see {@code docs/design/filter-dsl.md}).
 */
@Getter
@Setter
public class GizmoSearchDto extends AbstractSearchDto {

    private String name;

    private String nameLike;

    /**
     * {@code @Max(100)} exists purely so {@code SearchDtoValidationTest} can prove {@code
     * bindSearchDto} actually runs the bound {@code SearchDto} through the {@code Validator}, not
     * just binds it (Ground rules item 3 - this was silently ignored before). 100 is well above
     * every value {@code GizmoFilterTest} already exercises (<=10), so this is additive, not a
     * behavior change for any existing test.
     */
    @Max(100)
    private Integer quantityGte;

    private Integer quantityLte;

    private String codeNe;

    private List<String> codeIn;

    /** Ground rules item 5: case-insensitive contains-match - see {@code GizmoFilterTest}. */
    private String nameILike;

    /** Ground rules item 5: index-friendly prefix match, no leading wildcard - see {@code GizmoFilterTest}. */
    private String nameStartsWith;
}
