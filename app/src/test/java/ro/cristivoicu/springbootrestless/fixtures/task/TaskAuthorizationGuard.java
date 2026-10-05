package ro.cristivoicu.springbootrestless.fixtures.task;

import jakarta.servlet.http.HttpServletRequest;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

/**
 * Header-based (same idiom as {@code GadgetRestlessResource}'s own guard) "you may only touch
 * your own rows" guard - {@code canAccess} is the one override needed: the new {@code
 * canAccessAfterWrite} default already delegates to it, which is exactly what proves Ground
 * rules item 2's post-image check without this fixture needing to know anything about it.
 */
public class TaskAuthorizationGuard implements AuthorizationGuard<Task> {

    static final String USER_HEADER = "X-User";

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, Task entity) {
        String currentUser = request.getHeader(USER_HEADER);
        return currentUser != null && currentUser.equals(entity.getOwnerUsername());
    }
}
