package ro.cristivoicu.springbootrestless.example.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT Bearer auth (OAuth2 Resource Server), scoped to {@code /employees-dynamic/**} only -
 * everything else stays {@code permitAll()}, same "opt-in per resource" spirit as {@code
 * AuthorizationGuard} itself: this is a demo of one resource wired to a real Cerbos-backed guard
 * ({@link ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeRestlessResource}),
 * not a blanket lockdown of every entity this module happens to also expose.
 * <p>
 * {@link CerbosPrincipalResolver}-in-the-{@code cerbos}-module reads whatever {@code
 * Authentication} this chain populates - a validated JWT becomes a {@code JwtAuthenticationToken}
 * whose claims (beyond {@code sub}) become Cerbos principal attributes (see {@code
 * policies/employee.yaml}'s {@code scopedLastName} condition).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cerbos.demo.jwt.secret:please-change-this-demo-only-secret-key-1234567890}")
    private String demoJwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/employees-dynamic/**").authenticated()
                        .anyRequest().permitAll())
                // Stateless JSON API with no cookie-based session and no browser form post - the
                // CSRF token flow CSRF protection assumes doesn't apply here, same reasoning
                // RestlessResourceHandler's own hand-rolled body/id parsing already lives by.
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder(demoJwtSecret))));
        return http.build();
    }

    /**
     * Demo-only decoder: a fixed, symmetric (HS256) secret from a property, so this module's own
     * tests can mint valid tokens without standing up a real identity provider. A real deployment
     * would point {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (or {@code
     * jwk-set-uri}) at an actual IdP instead and drop this bean entirely.
     */
    private static JwtDecoder jwtDecoder(String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
