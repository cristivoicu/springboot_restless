package ro.cristivoicu.springbootrestless.cerbos;

import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Test;
import ro.cristivoicu.springbootrestless.cerbos.fixtures.MaskableDto;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link CerbosFieldMasker#mask(Map, Object)} against hand-built outputs maps - the
 * exact shape {@code CheckResult.getOutputs().asMap()} returns - without needing a running Cerbos
 * PDP at all (its constructors are package-private to the SDK, so a real {@code CheckResult}
 * can't be built here). The batched/RPC-calling overloads are covered end-to-end instead, by
 * {@code EmployeeAuthorizationGuardTest} in the {@code example} module.
 */
class CerbosFieldMaskerTest {

    @Test
    void emptyOutputsMaskNothing() {
        MaskableDto dto = new MaskableDto(1L, "Ada", "s3cr3t", false);

        CerbosFieldMasker.mask(Map.of(), dto);

        assertThat(dto.getName()).isEqualTo("Ada");
        assertThat(dto.getSecret()).isEqualTo("s3cr3t");
    }

    @Test
    void aStringOutputMasksTheFieldWithThatKey() {
        MaskableDto dto = new MaskableDto(1L, "Ada", "s3cr3t", false);

        CerbosFieldMasker.mask(Map.of("hide", stringValue("name")), dto);

        assertThat(dto.getName()).isNull();
        assertThat(dto.getSecret()).isEqualTo("s3cr3t"); // different key, untouched
    }

    @Test
    void aListOutputMasksEveryFieldItNames() {
        MaskableDto dto = new MaskableDto(1L, "Ada", "s3cr3t", false);

        CerbosFieldMasker.mask(Map.of("hide", listValue("name", "secretKey")), dto);

        assertThat(dto.getName()).isNull();
        assertThat(dto.getSecret()).isNull();
    }

    @Test
    void customAnnotationValueIsTheKeyLookedUpNotTheFieldName() {
        MaskableDto dto = new MaskableDto(1L, "Ada", "s3cr3t", false);

        // "secret" (the Java field name) is NOT the key - @CerbosHiddenField("secretKey") is.
        CerbosFieldMasker.mask(Map.of("hide", stringValue("secret")), dto);

        assertThat(dto.getSecret()).isEqualTo("s3cr3t");
    }

    @Test
    void unrelatedOutputValuesAreIgnoredNotFailed() {
        MaskableDto dto = new MaskableDto(1L, "Ada", "s3cr3t", false);

        CerbosFieldMasker.mask(Map.of("audit", Value.newBuilder().setNumberValue(42).build()), dto);

        assertThat(dto.getName()).isEqualTo("Ada");
    }

    @Test
    void maskingAnAnnotatedPrimitiveFieldFailsLoud() {
        MaskableDto dto = new MaskableDto(1L, "Ada", "s3cr3t", true);

        assertThatThrownBy(() -> CerbosFieldMasker.mask(Map.of("hide", stringValue("flag")), dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flag");
    }

    private static Value stringValue(String value) {
        return Value.newBuilder().setStringValue(value).build();
    }

    private static Value listValue(String... values) {
        ListValue.Builder list = ListValue.newBuilder();
        for (String value : values) {
            list.addValues(stringValue(value));
        }
        return Value.newBuilder().setListValue(list).build();
    }
}
