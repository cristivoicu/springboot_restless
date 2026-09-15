package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a Cerbos {@link Principal} from whatever Spring Security already populated -
 * deliberately independent of the concrete authentication mechanism, the same "read whatever the
 * app's own auth filter already populates" spirit as {@code AuthorizationGuard}'s javadoc, except
 * here the source is {@link SecurityContextHolder} rather than the raw {@link HttpServletRequest}.
 * {@link #resolve()} is the real entry point; {@link #resolve(HttpServletRequest)} (accepted for
 * symmetry with {@code AuthorizationGuard}'s hook signatures, and to leave room for a future
 * non-SecurityContext source) just delegates to it today - a {@code Mapper}, which has no
 * {@link HttpServletRequest} of its own to hand in, can call {@link #resolve()} directly.
 * <p>
 * {@link JwtAuthenticationToken} - the token type {@code spring-boot-starter-oauth2-resource-server}
 * populates for JWT Bearer auth - is special-cased: the subject becomes the principal id and the
 * remaining claims become Cerbos principal attributes, so policies can reference them (e.g.
 * {@code request.principal.attr.department}). Any other {@link Authentication} implementation
 * still works, just without extra attributes beyond id and roles.
 */
public final class CerbosPrincipalResolver {

    private CerbosPrincipalResolver() {
    }

    public static Principal resolve(HttpServletRequest request) {
        return resolve();
    }

    /**
     * Throws if there's no authenticated principal - the right choice for {@code
     * AuthorizationGuard} hooks, which only ever run on routes a {@code SecurityFilterChain} has
     * already required authentication for (see {@link #tryResolve()} for callers, like a shared
     * {@code Mapper}, that might also run on an unauthenticated/{@code permitAll} route).
     */
    public static Principal resolve() {
        return tryResolve().orElseThrow(() ->
                // Should normally never be reached - the SecurityFilterChain protecting this route
                // is expected to have already rejected the request with 401 before a controller
                // (and therefore this guard) ever runs. Kept as a defensive fallback for a route
                // wired to a Cerbos-backed guard but misconfigured to permit anonymous access.
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated principal"));
    }

    /**
     * Same as {@link #resolve()}, but {@link Optional#empty()} instead of throwing when there's
     * no authenticated principal - for callers that might run on a route with no authentication
     * requirement at all (e.g. a {@code Mapper} shared between a Cerbos-guarded route and an
     * older, deliberately-open one), where "nothing to resolve" means "nothing to check against
     * Cerbos either", not an error.
     */
    public static Optional<Principal> tryResolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        String[] roles = rolesOf(authentication);
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(withJwtAttributes(Principal.newInstance(jwtAuth.getToken().getSubject(), roles), jwtAuth.getToken()));
        }
        return Optional.of(Principal.newInstance(authentication.getName(), roles));
    }

    private static String[] rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(CerbosPrincipalResolver::stripAuthorityPrefix)
                .toArray(String[]::new);
    }

    /**
     * Spring Security convention-prefixes authorities ({@code ROLE_}/{@code SCOPE_}) that Cerbos
     * roles have no use for - policies deal in plain role names.
     */
    private static String stripAuthorityPrefix(String authority) {
        if (authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length());
        }
        if (authority.startsWith("SCOPE_")) {
            return authority.substring("SCOPE_".length());
        }
        return authority;
    }

    private static Principal withJwtAttributes(Principal principal, Jwt jwt) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        jwt.getClaims().forEach((claim, value) -> {
            if (JwtClaimNames.SUB.equals(claim)) {
                return; // already the principal id
            }
            AttributeValue attributeValue = CerbosAttributeValues.from(value);
            if (attributeValue != null) {
                attributes.put(claim, attributeValue);
            }
        });
        return attributes.isEmpty() ? principal : principal.withAttributes(attributes);
    }
}
