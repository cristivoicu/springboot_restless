package ro.cristivoicu.springbootrestless.fixtures.gizmo;

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

    private Integer quantityGte;

    private Integer quantityLte;

    private String codeNe;

    private List<String> codeIn;
}
