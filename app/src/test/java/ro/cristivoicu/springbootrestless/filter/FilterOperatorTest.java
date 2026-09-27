package ro.cristivoicu.springbootrestless.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context needed - {@code FilterOperator} is a pure enum) for the
 * suffix-matching/type-validity logic {@code RestlessResourceHandler#getSpecification} relies on
 * to decide between "silently skip" (unknown base property) and {@code IllegalStateException}
 * (a real property whose type doesn't support the requested operator) - see that method's own
 * javadoc for why those two failure modes are deliberately different. This is the pure-logic half
 * of that decision; the reflective dispatch itself is covered end-to-end by {@code
 * GizmoFilterTest} for the "everything lines up" path.
 */
class FilterOperatorTest {

    @Test
    void matchesTheLongestApplicableSuffixAndRecoversTheBaseProperty() {
        assertThat(FilterOperator.forFieldName("ageGte")).isEqualTo(FilterOperator.GTE);
        assertThat(FilterOperator.GTE.basePropertyOf("ageGte")).isEqualTo("age");

        assertThat(FilterOperator.forFieldName("nameLike")).isEqualTo(FilterOperator.LIKE);
        assertThat(FilterOperator.forFieldName("codeNe")).isEqualTo(FilterOperator.NE);
        assertThat(FilterOperator.forFieldName("codeIn")).isEqualTo(FilterOperator.IN);
    }

    @Test
    void aFieldThatsNothingButTheSuffixDoesNotMatch() {
        // "Gte" alone has no base property before it - not a filterable field at all.
        assertThat(FilterOperator.forFieldName("Gte")).isNull();
    }

    @Test
    void aPlainFieldWithNoSuffixDoesNotMatch() {
        assertThat(FilterOperator.forFieldName("lastName")).isNull();
    }

    @Test
    void rangeOperatorsRequireAComparableEntityFieldType() {
        assertThat(FilterOperator.GTE.supports(Integer.class)).isTrue();
        assertThat(FilterOperator.LTE.supports(java.math.BigDecimal.class)).isTrue();
        assertThat(FilterOperator.GTE.supports(String.class)).isTrue(); // String implements Comparable<String> too
        assertThat(FilterOperator.GTE.supports(Object.class)).isFalse(); // plain Object doesn't
    }

    @Test
    void rangeOperatorsSupportAPrimitiveEntityFieldTypeByBoxingItFirst() {
        // Comparable.class.isAssignableFrom(int.class) is false on its own (autoboxing doesn't
        // apply to Class checks) - FilterOperator boxes first, so a primitive entity field still
        // works.
        assertThat(FilterOperator.GTE.supports(int.class)).isTrue();
        assertThat(FilterOperator.LT.supports(long.class)).isTrue();
    }

    @Test
    void likeRequiresACharSequenceEntityFieldType() {
        assertThat(FilterOperator.LIKE.supports(String.class)).isTrue();
        assertThat(FilterOperator.LIKE.supports(Integer.class)).isFalse();
    }

    @Test
    void neAndInHaveNoEntityFieldTypeRestriction() {
        assertThat(FilterOperator.NE.supports(Integer.class)).isTrue();
        assertThat(FilterOperator.NE.supports(String.class)).isTrue();
        // IN's real validity check is against the SearchDto field's own type (Collection), done
        // by the caller, not this method - see FilterOperator#IN's own javadoc.
        assertThat(FilterOperator.IN.supports(Integer.class)).isTrue();
    }
}
