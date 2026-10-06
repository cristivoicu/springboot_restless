package ro.cristivoicu.springbootrestless.cerbos;

import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression;
import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression.Operand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.Sensor;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.SensorRepository;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.Widget;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.WidgetRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link CerbosQueryPlanTranslator} against hand-built {@link Operand}/{@link
 * Expression} trees - the exact shape {@code CerbosBlockingClient.plan(...)}'s CONDITIONAL
 * responses carry - without needing a running Cerbos PDP at all. {@link
 * CerbosAuthorizationGuardIT} covers the end-to-end path (real PDP producing the plan this test
 * fabricates by hand).
 */
@DataJpaTest
class CerbosQueryPlanTranslatorTest {

    @Autowired
    private WidgetRepository repository;

    @Autowired
    private SensorRepository sensorRepository;

    @Test
    void eqFiltersOnAStringAttribute() {
        seed();
        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("eq", variable("department"), stringValue("engineering")));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactly("alpha");
    }

    @Test
    void neExcludesTheMatchingRow() {
        seed();
        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("ne", variable("department"), stringValue("engineering")));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactlyInAnyOrder("beta", "gamma");
    }

    @Test
    void eqAgainstANullLiteralTranslatesToIsNull() {
        seed();
        repository.save(new Widget(null, "delta", 4L, null));

        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("eq", variable("department"), nullValue()));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactly("delta");
    }

    @Test
    void neAgainstANullLiteralTranslatesToIsNotNull() {
        seed();
        repository.save(new Widget(null, "delta", 4L, null));

        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("ne", variable("department"), nullValue()));

        assertThat(repository.findAll(spec)).extracting(Widget::getName)
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    @Test
    void numericComparisonCoercesTheDoubleLiteralToTheAttributesActualType() {
        seed();
        // ownerId is a Long column; Cerbos's Value only ever carries a double for numbers - this
        // is the exact case CerbosQueryPlanTranslator.coerce() exists for.
        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("gt", variable("ownerId"), numberValue(1)));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactlyInAnyOrder("beta", "gamma");
    }

    @Test
    void stringLiteralCoercesToAnEnumConstant() {
        sensorRepository.save(new Sensor(null, "sensor-a", Sensor.Status.ACTIVE, UUID.randomUUID(), Instant.now()));
        sensorRepository.save(new Sensor(null, "sensor-b", Sensor.Status.INACTIVE, UUID.randomUUID(), Instant.now()));

        Specification<Sensor> spec = CerbosQueryPlanTranslator.translate(
                expression("eq", rawVariable("request.resource.attr.status"), stringValue("ACTIVE")));

        assertThat(sensorRepository.findAll(spec)).extracting(Sensor::getName).containsExactly("sensor-a");
    }

    @Test
    void stringLiteralCoercesToAUuid() {
        UUID target = UUID.randomUUID();
        sensorRepository.save(new Sensor(null, "sensor-a", Sensor.Status.ACTIVE, target, Instant.now()));
        sensorRepository.save(new Sensor(null, "sensor-b", Sensor.Status.ACTIVE, UUID.randomUUID(), Instant.now()));

        Specification<Sensor> spec = CerbosQueryPlanTranslator.translate(
                expression("eq", rawVariable("request.resource.attr.externalId"), stringValue(target.toString())));

        assertThat(sensorRepository.findAll(spec)).extracting(Sensor::getName).containsExactly("sensor-a");
    }

    @Test
    void stringLiteralCoercesToAnInstant() {
        Instant target = Instant.parse("2025-01-01T00:00:00Z");
        sensorRepository.save(new Sensor(null, "sensor-a", Sensor.Status.ACTIVE, UUID.randomUUID(), target));
        sensorRepository.save(new Sensor(null, "sensor-b", Sensor.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2025-06-01T00:00:00Z")));

        Specification<Sensor> spec = CerbosQueryPlanTranslator.translate(
                expression("eq", rawVariable("request.resource.attr.installedAt"), stringValue(target.toString())));

        assertThat(sensorRepository.findAll(spec)).extracting(Sensor::getName).containsExactly("sensor-a");
    }

    @Test
    void inMatchesAnyOfTheListedLiterals() {
        seed();
        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("in", variable("department"), listValue("engineering", "sales")));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void andCombinesBothSides() {
        seed();
        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("and",
                        nested(expression("eq", variable("department"), stringValue("engineering"))),
                        nested(expression("gt", variable("ownerId"), numberValue(0)))));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactly("alpha");
    }

    @Test
    void notNegatesTheNestedExpression() {
        seed();
        Specification<Widget> spec = CerbosQueryPlanTranslator.translate(
                expression("not", nested(expression("eq", variable("department"), stringValue("engineering")))));

        assertThat(repository.findAll(spec)).extracting(Widget::getName).containsExactlyInAnyOrder("beta", "gamma");
    }

    @Test
    void unsupportedOperatorFailsLoudRatherThanUnderRestricting() {
        Operand condition = expression("matches", variable("department"), stringValue("eng.*"));

        assertThatThrownBy(() -> CerbosQueryPlanTranslator.<Widget>translate(condition)
                .toPredicate(null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matches");
    }

    @Test
    void unsupportedVariableNamespaceFailsLoud() {
        // A raw "request.principal.attr.role" (not the "request.resource.attr." shape this
        // translator handles) - the operand-parsing check catches it before root/cb are ever
        // dereferenced, so passing null for both here is safe.
        Operand condition = expression("eq", rawVariable("request.principal.attr.role"), stringValue("admin"));

        assertThatThrownBy(() -> CerbosQueryPlanTranslator.<Widget>translate(condition).toPredicate(null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request.principal.attr.role");
    }

    private void seed() {
        repository.save(new Widget(null, "alpha", 1L, "engineering"));
        repository.save(new Widget(null, "beta", 2L, "sales"));
        repository.save(new Widget(null, "gamma", 3L, "marketing"));
    }

    // ---- tiny builders mirroring exactly what CerbosBlockingClient.plan(...) hands back ----

    private static Operand expression(String operator, Operand... operands) {
        return Operand.newBuilder()
                .setExpression(Expression.newBuilder()
                        .setOperator(operator)
                        .addAllOperands(List.of(operands)))
                .build();
    }

    private static Operand nested(Operand expressionOperand) {
        return expressionOperand;
    }

    private static Operand variable(String field) {
        return rawVariable("request.resource.attr." + field);
    }

    private static Operand rawVariable(String variable) {
        return Operand.newBuilder().setVariable(variable).build();
    }

    private static Operand stringValue(String value) {
        return Operand.newBuilder().setValue(Value.newBuilder().setStringValue(value)).build();
    }

    private static Operand numberValue(double value) {
        return Operand.newBuilder().setValue(Value.newBuilder().setNumberValue(value)).build();
    }

    private static Operand nullValue() {
        return Operand.newBuilder()
                .setValue(Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE))
                .build();
    }

    private static Operand listValue(String... values) {
        ListValue.Builder list = ListValue.newBuilder();
        for (String value : values) {
            list.addValues(Value.newBuilder().setStringValue(value));
        }
        return Operand.newBuilder().setValue(Value.newBuilder().setListValue(list)).build();
    }
}
