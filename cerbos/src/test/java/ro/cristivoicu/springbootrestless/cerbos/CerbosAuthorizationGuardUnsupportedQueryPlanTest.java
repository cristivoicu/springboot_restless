package ro.cristivoicu.springbootrestless.cerbos;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.Widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ground rules Phase 2 item 11 ("Cerbos plan translator"): {@link CerbosQueryPlanTranslator}
 * always throws {@link IllegalStateException} on a query plan shape it can't translate (by
 * design - see its own javadoc), but that throw happens lazily, inside whatever repository call
 * actually evaluates the {@link Specification} {@code scope()} returned - previously nothing
 * caught it there, so an unsupported operator surfaced as an unhandled 500 on the request that
 * triggered the query. {@link CerbosAuthorizationGuard#scope} now wraps the translated
 * specification via the package-private {@code failClosedOnUnsupportedShape}, under test here
 * directly (see that method's own javadoc for why: reproducing this through a real PDP depends on
 * exactly which CEL shapes its planner leaves unresolved, which isn't something this project
 * controls).
 */
class CerbosAuthorizationGuardUnsupportedQueryPlanTest {

    @Test
    void translatorThrowingDuringEvaluationFailsClosedInsteadOfPropagating() {
        CerbosAuthorizationGuard<Widget> guard = new CerbosAuthorizationGuard<>(
                null, "widget", Widget::getId, CerbosResourceAttributesMapper.reflective(Widget.class));
        Specification<Widget> alwaysThrows = (root, query, cb) -> {
            throw new IllegalStateException("Unsupported Cerbos query plan operator: 'matches'");
        };

        Specification<Widget> failClosed = guard.failClosedOnUnsupportedShape(alwaysThrows, "read:list");

        @SuppressWarnings("unchecked")
        Root<Widget> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<Widget> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate denyAll = mock(Predicate.class);
        when(cb.disjunction()).thenReturn(denyAll);

        Predicate result = failClosed.toPredicate(root, query, cb);

        assertThat(result).isSameAs(denyAll);
    }
}
