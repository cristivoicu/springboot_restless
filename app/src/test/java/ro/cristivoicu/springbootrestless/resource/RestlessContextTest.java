package ro.cristivoicu.springbootrestless.resource;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature A (RestlessContext) - pure unit test, no Spring context. {@link RestlessResourceHandler}
 * is the only production caller of {@link RestlessContext#build}/{@link RestlessContext#runWith}
 * (both package-private), so this test lives in the same package to reach them directly, the same
 * reasoning every other {@code resource}-package unit test here already follows.
 */
class RestlessContextTest {

    @Test
    void currentIsNullOutsideAnyRunWithCall() {
        assertThat(RestlessContext.current()).isNull();
    }

    @Test
    void currentReturnsTheInstalledContextInsideRunWith() throws Exception {
        RestlessContext ctx = buildContext(Map.of("id", "42"));

        RestlessContext.runWith(ctx, () -> {
            assertThat(RestlessContext.current()).isSameAs(ctx);
            return null;
        });

        assertThat(RestlessContext.current()).isNull();
    }

    @Test
    void nestedRunWithRestoresTheOuterContextAfterwards() throws Exception {
        RestlessContext outer = buildContext(Map.of("outer", "1"));
        RestlessContext inner = buildContext(Map.of("inner", "2"));

        RestlessContext.runWith(outer, () -> {
            assertThat(RestlessContext.current()).isSameAs(outer);

            RestlessContext.runWith(inner, () -> {
                assertThat(RestlessContext.current()).isSameAs(inner);
                return null;
            });

            // Restored to the outer context, not cleared to null - the exact nested-call
            // scenario (RestlessEmbed resolving another resource's own findEmbeddedList/
            // findEmbeddedOne mid-request) this method's own javadoc describes.
            assertThat(RestlessContext.current()).isSameAs(outer);
            return null;
        });

        assertThat(RestlessContext.current()).isNull();
    }

    @Test
    void runWithRestoresEvenWhenTheBodyThrows() {
        RestlessContext ctx = buildContext(Map.of());

        assertThatThrownBy(() -> RestlessContext.runWith(ctx, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(RestlessContext.current()).isNull();
    }

    @Test
    void pathVariableConvertsViaTheRealConversionService() {
        UUID id = UUID.randomUUID();
        RestlessContext ctx = buildContext(Map.of("eventId", id.toString()));

        assertThat(ctx.pathVariable("eventId", UUID.class)).isEqualTo(id);
    }

    @Test
    void pathVariableIsNullWhenTheNameWasNeverMatched() {
        RestlessContext ctx = buildContext(Map.of());

        assertThat(ctx.pathVariable("missing", Long.class)).isNull();
    }

    @Test
    void pathVariableTranslatesAConversionFailureToABadRequest() {
        RestlessContext ctx = buildContext(Map.of("eventId", "not-a-uuid"));

        assertThatThrownBy(() -> ctx.pathVariable("eventId", UUID.class))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("eventId");
    }

    private static RestlessContext buildContext(Map<String, String> pathVariables) {
        return RestlessContext.build(pathVariables, new MockHttpServletRequest(),
                AuthorizationGuard.Action.READ_ONE, null, DefaultConversionService.getSharedInstance());
    }
}
