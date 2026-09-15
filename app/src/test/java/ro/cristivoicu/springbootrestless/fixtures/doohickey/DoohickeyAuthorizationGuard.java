package ro.cristivoicu.springbootrestless.fixtures.doohickey;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

/**
 * Escape-hatch proof: referenced via {@code @RestlessEntity(authorizationGuard =
 * DoohickeyAuthorizationGuard.class)} on {@link Doohickey}, so the generated {@code
 * DoohickeyRestlessResource} injects this hand-written {@code @Component} as a constructor
 * parameter and overrides {@code getAuthorizationGuard()} to return it - exactly the same
 * generated-constructor-injection mechanism {@code createDataSource} etc. already use for their
 * own verbs. Denies bulk delete unconditionally (and nothing else) purely as an observable
 * signal, distinct from {@code RestlessResourceHandler}'s default-permissive {@code
 * AuthorizationGuard.allowAll()}, that this specific bean is the one actually being consulted.
 */
@Component
public class DoohickeyAuthorizationGuard implements AuthorizationGuard<Doohickey> {

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        return action != Action.DELETE_ALL;
    }
}
