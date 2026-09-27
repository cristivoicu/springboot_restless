package ro.cristivoicu.springbootrestless.cerbos;

import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Default mapping from {@link AuthorizationGuard.Action} (plus, for {@code CUSTOM_READ}/{@code
 * NAMED_VIEW}/{@code WRITE_ACTION}, the action's own name) to the action string a Cerbos policy
 * checks against - lowercase enum name ({@code CREATE} -> {@code "create"}, {@code READ_ONE} ->
 * {@code "read_one"}, ...), and the custom action's/named view's/write action's own name verbatim
 * for those three (so a policy authorizes {@code "byEmailDomain"}, {@code "billing"}, or {@code
 * "promote"} directly, not a generic {@code "custom_read"}/{@code "named_view"}/{@code
 * "write_action"} bucket every one would otherwise share, indistinguishably, regardless of which
 * specific action/view/command a principal is actually attempting). A {@link BiFunction} rather
 * than a hardcoded static call so {@link CerbosAuthorizationGuard} can be pointed at a
 * differently-named policy without subclassing.
 */
public final class CerbosActionNaming {

    private CerbosActionNaming() {
    }

    public static final BiFunction<AuthorizationGuard.Action, String, String> DEFAULT = CerbosActionNaming::nameOf;

    public static String nameOf(AuthorizationGuard.Action action, String customActionName) {
        if (action == AuthorizationGuard.Action.CUSTOM_READ || action == AuthorizationGuard.Action.NAMED_VIEW
                || action == AuthorizationGuard.Action.WRITE_ACTION) {
            return customActionName;
        }
        return action.name().toLowerCase(Locale.ROOT);
    }
}
