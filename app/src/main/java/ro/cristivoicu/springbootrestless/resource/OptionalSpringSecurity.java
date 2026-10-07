package ro.cristivoicu.springbootrestless.resource;

import org.springframework.util.ClassUtils;

/**
 * Isolates every direct reference to Spring Security types behind a classpath presence check -
 * {@code app} has no mandatory Spring Security dependency (see its {@code pom.xml}), the same
 * reasoning {@code RestlessOpenApiCustomizer}/{@code RestlessAuthorizationMetrics} already apply
 * to springdoc/Actuator via {@code @ConditionalOnClass}. Those gate a whole <em>bean</em>,
 * conditionally registered; this gates a whole <em>nested class</em> instead, since {@link
 * RestlessContext} is built on every single request regardless of whether Spring Security is
 * present, so it can never risk eagerly resolving a type that might not be there. {@code
 * Accessor} is a separate class file, loaded by the JVM only the first time {@link
 * #currentAuthentication()} actually reaches into it - guarded by {@link #PRESENT}, so a consumer
 * with no {@code spring-security-core} on their runtime classpath never triggers that load, never
 * risking a {@code NoClassDefFoundError}.
 */
final class OptionalSpringSecurity {

    private static final boolean PRESENT = ClassUtils.isPresent(
            "org.springframework.security.core.context.SecurityContextHolder", null);

    private OptionalSpringSecurity() {
    }

    static Object currentAuthentication() {
        return PRESENT ? Accessor.current() : null;
    }

    private static final class Accessor {
        static Object current() {
            return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        }
    }
}
