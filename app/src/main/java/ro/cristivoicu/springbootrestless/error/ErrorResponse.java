package ro.cristivoicu.springbootrestless.error;

import java.time.Instant;
import java.util.List;

/**
 * The one error body shape every route this framework registers (generated or hand-wired alike)
 * responds with, via {@link RestlessExceptionHandler} - see its javadoc for why this needed to
 * exist at all. {@code details} is field-level validation messages ({@code "firstName: must not
 * be blank"}) when the failure was a bean-validation one, empty otherwise - never {@code null},
 * so a client never has to branch on its absence.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
                             List<String> details) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
