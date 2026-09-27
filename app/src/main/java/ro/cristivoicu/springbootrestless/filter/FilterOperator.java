package ro.cristivoicu.springbootrestless.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.util.ClassUtils;

import java.util.Collection;

/**
 * A field-name suffix convention {@code RestlessResourceHandler#getSpecification} recognizes
 * beyond plain equality - a {@code SearchDto} field named {@code ageGte} filters {@code age >=
 * value} instead of {@code age == value}, reflected over the same way an unsuffixed field
 * already is. See {@code docs/design/filter-dsl.md} for the full design reasoning (why this
 * suffix convention over an RSQL-style query string, and why the wire format is camelCase -
 * {@code ?ageGte=30} - not snake_case).
 * <p>
 * Suffix matching is exact-case and requires at least one character before the suffix (a field
 * literally named {@code "Gte"} alone doesn't match itself onto an empty base property) - see
 * {@link #forFieldName}. No two suffixes here overlap ({@code "Gte"} doesn't end in {@code "Gt"},
 * {@code "Lte"} doesn't end in {@code "Lt"}), so match order never matters.
 * <p>
 * Each operator's {@link #supports} check runs against the <em>entity's</em> declared field type
 * (what actually gets compared in the generated SQL), not the {@code SearchDto} field's own type -
 * except {@link #IN}, whose validity is about the {@code SearchDto} field itself needing to be a
 * {@link Collection} (checked separately by the caller, not via this method) rather than anything
 * about the entity field's type.
 */
public enum FilterOperator {

    GTE("Gte") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return isComparable(entityFieldType);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
        }
    },
    LTE("Lte") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return isComparable(entityFieldType);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
        }
    },
    GT("Gt") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return isComparable(entityFieldType);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return cb.greaterThan((Path<Comparable>) path, (Comparable) value);
        }
    },
    LT("Lt") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return isComparable(entityFieldType);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return cb.lessThan((Path<Comparable>) path, (Comparable) value);
        }
    },
    /** Contains-match ({@code %value%}), not prefix/suffix-only - the most generally useful default for a generic LIKE suffix. */
    LIKE("Like") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return CharSequence.class.isAssignableFrom(entityFieldType);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return cb.like((Path<String>) path, "%" + value + "%");
        }
    },
    NE("Ne") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return true;
        }

        @Override
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return cb.notEqual(path, value);
        }
    },
    /**
     * Validity for this one is about the {@code SearchDto} field's own declared type ({@code
     * Collection}), not the entity field's - {@link #supports} always returns {@code true} here;
     * {@code RestlessResourceHandler#getSpecification}'s own {@code IN} branch checks the
     * {@code SearchDto} side directly instead of going through this method.
     */
    IN("In") {
        @Override
        public boolean supports(Class<?> entityFieldType) {
            return true;
        }

        @Override
        public Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value) {
            return path.in((Collection<?>) value);
        }
    };

    private final String suffix;

    FilterOperator(String suffix) {
        this.suffix = suffix;
    }

    /** {@code null} when {@code fieldName} doesn't end with any recognized suffix (or is nothing but the suffix itself, with no base property before it). */
    public static FilterOperator forFieldName(String fieldName) {
        for (FilterOperator operator : values()) {
            if (fieldName.length() > operator.suffix.length() && fieldName.endsWith(operator.suffix)) {
                return operator;
            }
        }
        return null;
    }

    /** {@code "ageGte"} -&gt; {@code "age"} - the entity property name this operator filters on. */
    public String basePropertyOf(String fieldName) {
        return fieldName.substring(0, fieldName.length() - suffix.length());
    }

    public abstract boolean supports(Class<?> entityFieldType);

    public abstract Predicate predicate(CriteriaBuilder cb, Path<?> path, Object value);

    /** {@link Comparable#isAssignableFrom} against a primitive type is always {@code false} (autoboxing doesn't apply to {@code Class} checks) - box first. */
    private static boolean isComparable(Class<?> type) {
        return Comparable.class.isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(type));
    }
}
