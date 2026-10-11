package ro.cristivoicu.springbootrestless.filter;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A small fluent builder for hand-written {@code Specification} escape hatches - overridden
 * {@code getSpecification()} methods, custom read actions - not a replacement for the
 * reflection-driven default filter {@link FilterOperator} extends. Every method is null-safe the
 * same way that default filter already is: a null/blank/empty value is silently a no-op, not an
 * error, so a caller doesn't need its own "is this filter actually set" guard the way the
 * hand-written lambdas this builder replaces used to need by hand (compare {@code
 * GadgetRestlessResource}'s own {@code getSpecification()}/{@code byEmailDomain} action before
 * and after this existed).
 *
 * @param <E> the entity type this specification filters
 */
public final class RestlessSpecifications<E> {

    private final List<Specification<E>> predicates = new ArrayList<>();

    private RestlessSpecifications() {
    }

    public static <E> RestlessSpecifications<E> builder() {
        return new RestlessSpecifications<>();
    }

    public RestlessSpecifications<E> eq(String property, Object value) {
        return isAbsent(value) ? this : add((root, query, cb) -> cb.equal(root.get(property), value));
    }

    public RestlessSpecifications<E> ne(String property, Object value) {
        return isAbsent(value) ? this : add((root, query, cb) -> cb.notEqual(root.get(property), value));
    }

    /** {@code pattern} is used verbatim - include your own {@code %} wildcards, same as {@link jakarta.persistence.criteria.CriteriaBuilder#like} itself. */
    public RestlessSpecifications<E> like(String property, String pattern) {
        return !StringUtils.hasText(pattern) ? this : add((root, query, cb) -> cb.like(root.get(property), pattern));
    }

    public <Y extends Comparable<? super Y>> RestlessSpecifications<E> gte(String property, Y value) {
        return value == null ? this : add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(property), value));
    }

    public <Y extends Comparable<? super Y>> RestlessSpecifications<E> lte(String property, Y value) {
        return value == null ? this : add((root, query, cb) -> cb.lessThanOrEqualTo(root.get(property), value));
    }

    public <Y extends Comparable<? super Y>> RestlessSpecifications<E> gt(String property, Y value) {
        return value == null ? this : add((root, query, cb) -> cb.greaterThan(root.get(property), value));
    }

    public <Y extends Comparable<? super Y>> RestlessSpecifications<E> lt(String property, Y value) {
        return value == null ? this : add((root, query, cb) -> cb.lessThan(root.get(property), value));
    }

    /** A no-op unless both bounds are present - a caller that only has one bound should use {@link #gte}/{@link #lte} directly instead. */
    public <Y extends Comparable<? super Y>> RestlessSpecifications<E> between(String property, Y from, Y to) {
        return from == null || to == null ? this : add((root, query, cb) -> cb.between(root.get(property), from, to));
    }

    public RestlessSpecifications<E> in(String property, Collection<?> values) {
        return values == null || values.isEmpty() ? this : add((root, query, cb) -> root.get(property).in(values));
    }

    /**
     * Rows whose {@code ownerProperty} equals {@code principalId}; a {@code null} principal
     * matches nothing (fail closed) rather than being skipped like other absent filter values.
     */
    public RestlessSpecifications<E> ownedBy(String ownerProperty, Object principalId) {
        return add(principalId == null
                ? (root, query, cb) -> cb.disjunction()
                : (root, query, cb) -> cb.equal(root.get(ownerProperty), principalId));
    }

    /**
     * Rows whose {@code linkProperty} points at a {@code parentType} row (by its {@code
     * parentIdProperty}) whose {@code parentOwnerProperty} equals {@code principalId} - one SQL
     * subquery, for hand-written guards' {@code scope()}. A {@code null} principal matches nothing.
     */
    public RestlessSpecifications<E> ownedVia(String linkProperty, Class<?> parentType, String parentIdProperty,
                                              String parentOwnerProperty, Object principalId) {
        if (principalId == null) {
            return add((root, query, cb) -> cb.disjunction());
        }
        return add((root, query, cb) -> {
            Class<?> idType = root.get(linkProperty).getJavaType();
            @SuppressWarnings({"unchecked", "rawtypes"})
            jakarta.persistence.criteria.Subquery<Object> owned = (jakarta.persistence.criteria.Subquery) query.subquery(idType);
            jakarta.persistence.criteria.Root<?> parent = owned.from(parentType);
            owned.select(parent.get(parentIdProperty)).where(cb.equal(parent.get(parentOwnerProperty), principalId));
            return root.get(linkProperty).in(owned);
        });
    }

    private RestlessSpecifications<E> add(Specification<E> spec) {
        predicates.add(spec);
        return this;
    }

    /** {@code cb.conjunction()} (unrestricted) when nothing was ever added - same "no filters means everything matches" default {@code getSpecification} itself falls back to. */
    public Specification<E> build() {
        return predicates.stream().reduce(Specification::and).orElse((root, query, cb) -> cb.conjunction());
    }

    private static boolean isAbsent(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof CharSequence text && !StringUtils.hasText(text.toString());
    }
}
