package ro.cristivoicu.springbootrestless.authorization;

import ro.cristivoicu.springbootrestless.resource.RestlessContext;

import java.security.Principal;

/**
 * Who is calling, as the id an owner column stores.
 * <p>
 * Read by {@link RestlessContext#principalId()} - and through it by {@code @RestlessFromPrincipal},
 * {@code @RestlessOwnedBy}/{@code RestlessOwnershipGuards}, and any hand-written guard or data
 * source that needs it. {@code null} means "no principal": everything built on this treats that as
 * a denial (fail closed), never as a wildcard.
 * <p>
 * The default ({@link #AUTHENTICATION_NAME}) is the authenticated principal's name - the JWT
 * {@code sub} for a resource server, the username for form login - with an anonymous
 * authentication counting as none. Register your own bean when rows are owned by something else,
 * e.g. a local user id provisioned from the token:
 * <pre>{@code
 * @Bean
 * RestlessPrincipalIdResolver principalIdResolver(CurrentUserService users) {
 *     return context -> context.authentication() == null ? null : users.require().getId();
 * }
 * }</pre>
 * The returned value is converted to each owner field's type with the application's
 * {@code ConversionService}, so a {@code String} subject can back a {@code UUID} column.
 */
@FunctionalInterface
public interface RestlessPrincipalIdResolver {

    /** The current principal's id, or {@code null} if there is none. Called at most once per request. */
    Object resolve(RestlessContext context);

    RestlessPrincipalIdResolver AUTHENTICATION_NAME = context -> authenticationName(context.authentication());

    /**
     * Matched by simple name, not type: Spring Security is an optional dependency of this module
     * (the same reasoning {@code OptionalSpringSecurity} documents). Its {@code Authentication}
     * extends {@link Principal}, which is all that's needed here.
     */
    private static Object authenticationName(Object authentication) {
        if (!(authentication instanceof Principal principal)
                || authentication.getClass().getSimpleName().equals("AnonymousAuthenticationToken")) {
            return null;
        }
        String name = principal.getName();
        return name == null || name.isBlank() ? null : name;
    }
}
