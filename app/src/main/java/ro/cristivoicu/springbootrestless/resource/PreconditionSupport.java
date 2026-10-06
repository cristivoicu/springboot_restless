package ro.cristivoicu.springbootrestless.resource;

import jakarta.persistence.Version;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

/**
 * Extracted out of {@code RestlessResourceHandler} (Ground rules Phase 2 item 14 - "no
 * public-surface change": these were already {@code private} there, so moving them changes
 * nothing any subclass/consumer could see). Static, not instance state - entirely a function of
 * the {@code If-Match}/{@code If-None-Match} header strings and the entity instance handed in,
 * generic over {@code Object} rather than the handler's own {@code E} since none of this
 * actually needs it to be that specific.
 * <p>
 * Ground rules Phase 2 item 9 ("Conditional requests") lives here: {@link #readVersion} walks
 * superclasses ({@code @Version} on a shared {@code @MappedSuperclass} previously wasn't found
 * at all), {@link #eTagOf} builds the strong {@code ETag} {@code RestlessResourceHandler} now
 * emits on single-resource responses, {@link #matchesIfNoneMatch} implements the <em>weak</em>
 * comparison {@code If-None-Match} uses per RFC 9110, and {@link #checkIfMatch} implements the
 * <em>strong</em> comparison {@code If-Match} uses - including {@code *} and a comma-separated
 * candidate list, and excluding any weak (`W/`) candidate entirely, since a weak validator can
 * never satisfy strong comparison.
 */
final class PreconditionSupport {

    private PreconditionSupport() {
    }

    /**
     * The strong {@code ETag} header value for {@code entity} - {@code "<version>"}, quoted, no
     * {@code W/} prefix - or {@link Optional#empty()} for an entity type with no {@code
     * @jakarta.persistence.Version} field at all (nothing to emit).
     */
    static Optional<String> eTagOf(Object entity) {
        return readVersion(entity).map(version -> "\"" + version + "\"");
    }

    /**
     * {@code If-None-Match} uses <em>weak</em> comparison (RFC 9110 §13.1.2): a {@code W/} prefix
     * on a candidate is stripped, not excluded, unlike {@link #checkIfMatch}'s strong comparison.
     * {@code *} always matches (the resource exists, which it does by the time this runs).
     */
    static boolean matchesIfNoneMatch(String ifNoneMatchHeader, String currentETag) {
        if (ifNoneMatchHeader == null || currentETag == null) {
            return false;
        }
        if ("*".equals(ifNoneMatchHeader.trim())) {
            return true;
        }
        String unwrappedCurrent = unwrap(currentETag);
        return Arrays.stream(ifNoneMatchHeader.split(","))
                .map(PreconditionSupport::unwrap)
                .anyMatch(candidate -> candidate.equals(unwrappedCurrent));
    }

    /**
     * Opt-in optimistic-concurrency precondition for a single-item write: a no-op whenever
     * {@code ifMatchHeader} is {@code null} (no header sent) or {@code entity}'s type has no
     * {@code @jakarta.persistence.Version} field at all (see {@link #readVersion}) - fully
     * backward compatible with every entity that predates this feature.
     * <p>
     * {@code *} always satisfies (the resource exists, which it does by the time this runs, by
     * definition - {@code checkCanAccess} already loaded it). Otherwise parses a comma-separated
     * candidate list and applies <b>strong</b> comparison (RFC 9110 §13.1.3): any candidate
     * carrying a {@code W/} prefix is <em>excluded</em> from the list entirely, not
     * stripped-and-compared - a weak validator can never satisfy a strong-comparison
     * precondition, no matter what its quoted value is. A disagreement is the client racing a
     * stale read against a write that already landed - reported as 412, not the 409 a raw {@code
     * ObjectOptimisticLockingFailureException} from an actual concurrent {@code save()} maps to
     * ({@code RestlessExceptionHandler}), since this check runs before any write is even
     * attempted.
     */
    static void checkIfMatch(String ifMatchHeader, Object entity) {
        if (ifMatchHeader == null) {
            return;
        }
        if ("*".equals(ifMatchHeader.trim())) {
            return;
        }
        readVersion(entity).ifPresent(actual -> {
            boolean satisfied = Arrays.stream(ifMatchHeader.split(","))
                    .map(String::trim)
                    .filter(candidate -> !candidate.startsWith("W/"))
                    .map(PreconditionSupport::unwrap)
                    .anyMatch(candidate -> candidate.equals(actual));
            if (!satisfied) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "If-Match '" + ifMatchHeader + "' does not match current version '" + actual + "'");
            }
        });
    }

    /**
     * Reflectively finds {@code entity}'s {@code @jakarta.persistence.Version} field (if any),
     * walking superclasses (a {@code @Version} on a shared {@code @MappedSuperclass} is common -
     * see {@code AbstractAuditableEntity} for the same pattern with audit fields - and {@code
     * getDeclaredFields()} on the concrete class alone would miss it entirely) - and returns its
     * current value as a string. {@link Optional#empty()} for an entity type with no such field,
     * which {@link #checkIfMatch} treats as "this entity doesn't support optimistic locking, so
     * an If-Match header on it can't be honored" rather than an error.
     */
    static Optional<String> readVersion(Object entity) {
        for (Class<?> type = entity.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
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
        }
        return Optional.empty();
    }

    /** Strips a leading weak-validator marker ({@code W/}) and surrounding quotes, so both a raw version number and a properly-quoted HTTP ETag are accepted. */
    private static String unwrap(String etag) {
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
