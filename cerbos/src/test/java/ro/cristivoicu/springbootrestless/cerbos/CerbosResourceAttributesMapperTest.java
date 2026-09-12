package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.builders.AttributeValue;
import org.junit.jupiter.api.Test;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.Widget;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CerbosResourceAttributesMapper#reflective} needs no running PDP, no JPA, no Spring
 * context at all - it's plain reflection over an entity instance - so this is a bare unit test.
 */
class CerbosResourceAttributesMapperTest {

    @Test
    void exposesEveryFieldWithNoHandWrittenMapping() {
        Widget widget = new Widget(7L, "alpha", 42L, "engineering");
        CerbosResourceAttributesMapper<Widget> mapper = CerbosResourceAttributesMapper.reflective(Widget.class);

        Map<String, AttributeValue> attributes = mapper.attributesOf(widget);

        assertThat(attributes).containsOnlyKeys("id", "name", "ownerId", "department");
        assertThat(attributes.get("id").toValue().getNumberValue()).isEqualTo(7.0);
        assertThat(attributes.get("name").toValue().getStringValue()).isEqualTo("alpha");
        assertThat(attributes.get("ownerId").toValue().getNumberValue()).isEqualTo(42.0);
        assertThat(attributes.get("department").toValue().getStringValue()).isEqualTo("engineering");
    }

    @Test
    void aNullFieldIsOmittedRatherThanMappedToANullAttribute() {
        Widget widget = new Widget(1L, null, 1L, "engineering");
        CerbosResourceAttributesMapper<Widget> mapper = CerbosResourceAttributesMapper.reflective(Widget.class);

        assertThat(mapper.attributesOf(widget)).doesNotContainKey("name");
    }
}
