package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.Version;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Extracted out of {@code RestlessResourceHandler} (Ground rules Phase 2 item 14 - "no
 * public-surface change": {@code checkIfMatch}/{@code readVersion}/{@code stripEtagWrapper} were
 * already {@code private} there, so moving them changes nothing any subclass/consumer could
 * see). Static, not instance state - entirely a function of the {@code If-Match} header string
 * and the entity instance handed in, generic over {@code Object} rather than the handler's own
 * {@code E} since none of this actually needs it to be that specific.
 */
final class PreconditionSupport {

    private PreconditionSupport() {
    }

    /**
     * Opt-in optimistic-concurrency precondition for a single-item write: a no-op whenever
     * {@code ifMatch} is {@code null} (no header sent) or {@code entity}'s type has no {@code
     * @jakarta.persistence.Version} field at all (see {@link #readVersion}) - fully backward
     * compatible with every entity that predates this feature. When both are present and
     * disagree, this is the client racing a stale read against a write that already landed -
     * reported as 412, not the 409 a raw {@code ObjectOptimisticLockingFailureException} from an
     * actual concurrent {@code save()} maps to ({@code RestlessExceptionHandler}), since this
     * check runs before any write is even attempted.
     */
    static void checkIfMatch(String ifMatch, Object entity) {
        if (ifMatch == null) {
            return;
        }
        String expected = stripEtagWrapper(ifMatch);
        readVersion(entity).ifPresent(actual -> {
            if (!expected.equals(actual)) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "If-Match '" + ifMatch + "' does not match current version '" + actual + "'");
            }
        });
    }

    /**
     * Reflectively finds {@code entity}'s {@code @jakarta.persistence.Version} field (if any) and
     * returns its current value as a string - the same "scan declared fields for an annotation"
     * idiom {@code RestlessResourceHandler#getSpecification}'s default equality filter already
     * uses. {@link Optional#empty()} for an entity type with no such field, which {@link
     * #checkIfMatch} treats as "this entity doesn't support optimistic locking, so an If-Match
     * header on it can't be honored" rather than an error.
     */
    static Optional<String> readVersion(Object entity) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Version.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(entity);
                    return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Could not read @Version field " + field + " for If-Match support", e);
                }
            }
        }
        return Optional.empty();
    }

    /** Strips a leading weak-validator marker ({@code W/}) and surrounding quotes, so both a raw version number and a properly-quoted HTTP ETag are accepted as {@code If-Match}. */
    private static String stripEtagWrapper(String etag) {
        String value = etag.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
