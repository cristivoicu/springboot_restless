package ro.cristivoicu.springbootrestless.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JWT Bearer auth (OAuth2 Resource Server) against a real Keycloak realm, scoped to the three
 * Cerbos-guarded resources - {@code /employees-dynamic/**}, {@code /departments/**}, {@code
 * /projects/**} - only; everything else (the Stage-0 parity-testing {@code /employees/**}
 * controllers) stays {@code permitAll()}, same "opt-in per resource" spirit as {@code
 * AuthorizationGuard} itself: this locks down exactly the resources wired to a real Cerbos-backed
 * guard ({@link ro.cristivoicu.springbootrestless.example.entity.employee.EmployeeRestlessResource},
 * {@link ro.cristivoicu.springbootrestless.example.entity.department.DepartmentRestlessResource},
 * {@link ro.cristivoicu.springbootrestless.example.entity.project.ProjectRestlessResource}), not a
 * blanket lockdown of every entity this module happens to also expose.
 * <p>
 * No {@code JwtDecoder} bean here - {@code
 * spring.security.oauth2.resourceserver.jwt.issuer-uri} (see {@code application.properties},
 * pointed at the Keycloak realm {@code docker-compose.yml}/{@code
 * docker/keycloak/restless-demo-realm.json} start) is enough for Spring Boot's own
 * resource-server autoconfiguration to build one from the realm's OIDC discovery document.
 * <p>
 * {@link CerbosPrincipalResolver}-in-the-{@code cerbos}-module reads whatever {@code
 * Authentication} this chain populates - a validated JWT becomes a {@code JwtAuthenticationToken}
 * whose claims (beyond {@code sub}) become Cerbos principal attributes (see {@code
 * policies/employee.yaml}'s {@code scopedLastName}/{@code canViewSalary} claims). {@link
 * #jwtAuthenticationConverter()} is the one piece Keycloak specifically needs: Spring Security's
 * default authorities converter only reads a {@code scope}/{@code scp} claim, but Keycloak puts
 * realm roles under {@code realm_access.roles} instead.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/employees-dynamic/**", "/departments/**", "/projects/**").authenticated()
                        .anyRequest().permitAll())
                // Stateless JSON API with no cookie-based session and no browser form post - the
                // CSRF token flow CSRF protection assumes doesn't apply here, same reasoning
                // RestlessResourceHandler's own hand-rolled body/id parsing already lives by.
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Maps Keycloak's {@code realm_access: {roles: [...]}} claim to {@link GrantedAuthority}s -
     * verbatim, no {@code ROLE_}/{@code SCOPE_} prefix (Cerbos policies deal in plain role names;
     * {@code CerbosPrincipalResolver} would strip either prefix anyway if one were added here).
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRolesOf);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> realmRolesOf(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority(String.valueOf(role))).toList();
    }
}
