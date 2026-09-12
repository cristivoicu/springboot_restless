package ro.cristivoicu.springbootrestless.cerbos;

import com.google.protobuf.Value;
import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression;
import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression.Operand;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Translates the CONDITIONAL branch of a {@code CerbosBlockingClient.plan(...)} result into a
 * JPA {@link Specification}, so {@link CerbosAuthorizationGuard#scope} can AND it onto a list/page
 * query exactly like any other {@code AuthorizationGuard.scope()} implementation. Cerbos's Java
 * SDK has no built-in JPA/Hibernate adapter (some other-language SDKs ship SQL query-plan
 * helpers; this one doesn't), so this walks the returned AST by hand.
 * <p>
 * The AST ({@link Expression}/{@link Operand}, both plain protobuf messages) is a boolean
 * expression tree: an {@link Expression} is an {@code operator} plus a list of {@link Operand}s;
 * each {@link Operand} is itself one of three things (a protobuf {@code oneof}) - a nested {@link
 * Expression}, a literal {@link Value}, or a {@code variable} string of the form {@code
 * request.resource.attr.<field>}. Cerbos's planner already substitutes in everything it can
 * resolve without touching the database (principal attributes, literals) - what's left symbolic
 * is exactly the per-row resource attributes only the database can answer, which is what makes
 * this translatable into a {@code WHERE} clause at all.
 * <p>
 * <b>Fails loud, not quiet.</b> Every unsupported shape - an operator this class doesn't
 * recognize, a variable outside {@code request.resource.attr.*}, a literal kind with no JPA
 * equivalent - throws {@link IllegalStateException} rather than silently dropping a condition.
 * An authorization filter that quietly under-restricts on an unsupported case is a much worse
 * failure mode than a hard error surfaced at development time.
 */
final class CerbosQueryPlanTranslator {

    private static final String VARIABLE_PREFIX = "request.resource.attr.";

    private CerbosQueryPlanTranslator() {
    }

    static <E> Specification<E> translate(Operand condition) {
        return (root, query, cb) -> toPredicate(condition, root, cb);
    }

    private static Predicate toPredicate(Operand operand, Root<?> root, CriteriaBuilder cb) {
        if (!operand.hasExpression()) {
            throw new IllegalStateException(
                    "Expected a boolean expression at this position in the Cerbos query plan, got: " + operand);
        }
        return toPredicate(operand.getExpression(), root, cb);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate toPredicate(Expression expression, Root<?> root, CriteriaBuilder cb) {
        String operator = expression.getOperator();
        List<Operand> operands = expression.getOperandsList();
        return switch (operator) {
            case "and" -> cb.and(operands.stream().map(o -> toPredicate(o, root, cb)).toArray(Predicate[]::new));
            case "or" -> cb.or(operands.stream().map(o -> toPredicate(o, root, cb)).toArray(Predicate[]::new));
            case "not" -> cb.not(toPredicate(requireSingle(operands, "not"), root, cb));

            case "eq" -> cb.equal(pathOf(operands, 0, root), literalFor(operands, 1, pathOf(operands, 0, root)));
            case "ne" -> cb.notEqual(pathOf(operands, 0, root), literalFor(operands, 1, pathOf(operands, 0, root)));

            // Raw-type escape hatch: the JPA-attribute-typed Path<Y> these need can't be named
            // here since the field (and its Java type) are only known at runtime, via the
            // variable operand's own dotted path. Safe because literalFor() coerces the literal
            // to that same runtime type first.
            case "lt", "le", "gt", "ge" -> {
                Path path = pathOf(operands, 0, root);
                Comparable literal = (Comparable) literalFor(operands, 1, path);
                yield switch (operator) {
                    case "lt" -> cb.lessThan(path, literal);
                    case "le" -> cb.lessThanOrEqualTo(path, literal);
                    case "gt" -> cb.greaterThan(path, literal);
                    default -> cb.greaterThanOrEqualTo(path, literal);
                };
            }

            case "in" -> {
                Path path = pathOf(operands, 0, root);
                yield path.in(literalListFor(operands, 1, path));
            }

            default -> throw new IllegalStateException("Unsupported Cerbos query plan operator: '" + operator + "'");
        };
    }

    private static Operand requireSingle(List<Operand> operands, String operator) {
        if (operands.size() != 1) {
            throw new IllegalStateException("Expected exactly one operand for '" + operator + "', got " + operands.size());
        }
        return operands.get(0);
    }

    /**
     * Resolves operand {@code index}'s {@code request.resource.attr.<field>} variable to a JPA
     * {@link Path}, walking dotted field paths ({@code a.b}) via successive {@code Path.get(...)}
     * calls. Raw {@link Path} (not {@code Path<Object>}) - same reasoning as the raw-type
     * escape hatches in {@code toPredicate}: the attribute's Java type is only known at runtime.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Path pathOf(List<Operand> operands, int index, Root<?> root) {
        Operand operand = operands.get(index);
        if (!operand.hasVariable()) {
            throw new IllegalStateException("Expected a resource attribute variable at operand " + index + ", got: " + operand);
        }
        String variable = operand.getVariable();
        if (!variable.startsWith(VARIABLE_PREFIX)) {
            throw new IllegalStateException(
                    "Unsupported Cerbos query plan variable (expected '" + VARIABLE_PREFIX + "<field>'): " + variable);
        }
        Path path = root;
        for (String segment : variable.substring(VARIABLE_PREFIX.length()).split("\\.")) {
            path = path.get(segment);
        }
        return path;
    }

    /**
     * Resolves operand {@code index}'s literal {@link Value}, coerced to {@code path}'s actual
     * JPA attribute type. Coercion matters specifically for numbers: protobuf's {@link Value}
     * only ever carries a {@code double} for {@code NUMBER_VALUE}, so comparing it as-is against,
     * say, a {@code Long} id column would silently never match.
     */
    private static Object literalFor(List<Operand> operands, int index, Path<?> path) {
        return coerce(literalOf(operands.get(index)), path.getJavaType());
    }

    private static List<Object> literalListFor(List<Operand> operands, int index, Path<?> path) {
        Operand operand = operands.get(index);
        if (!operand.hasValue() || !operand.getValue().hasListValue()) {
            throw new IllegalStateException("Expected a list literal for an 'in' operand, got: " + operand);
        }
        return operand.getValue().getListValue().getValuesList().stream()
                .map(value -> coerce(literalOf(value), path.getJavaType()))
                .toList();
    }

    private static Object literalOf(Operand operand) {
        if (!operand.hasValue()) {
            throw new IllegalStateException("Expected a literal value operand, got: " + operand);
        }
        return literalOf(operand.getValue());
    }

    private static Object literalOf(Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case BOOL_VALUE -> value.getBoolValue();
            case NULL_VALUE -> null;
            default -> throw new IllegalStateException(
                    "Unsupported literal value in Cerbos query plan condition: " + value);
        };
    }

    private static Object coerce(Object literal, Class<?> targetType) {
        if (!(literal instanceof Double number)) {
            return literal;
        }
        if (targetType == Long.class || targetType == long.class) {
            return number.longValue();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return number.intValue();
        }
        if (targetType == Short.class || targetType == short.class) {
            return number.shortValue();
        }
        if (targetType == Float.class || targetType == float.class) {
            return number.floatValue();
        }
        if (targetType == java.math.BigDecimal.class) {
            return java.math.BigDecimal.valueOf(number);
        }
        if (targetType == java.math.BigInteger.class) {
            return java.math.BigInteger.valueOf(number.longValue());
        }
        return literal;
    }
}
