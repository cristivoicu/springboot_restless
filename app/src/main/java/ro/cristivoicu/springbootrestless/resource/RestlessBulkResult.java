package ro.cristivoicu.springbootrestless.resource;

import java.util.List;

/**
 * The response of a {@code PARTIAL} bulk create: what was created, what was skipped as a duplicate,
 * and why each rejected row was rejected ({@code index} is its 0-based position in the request).
 */
public record RestlessBulkResult(int created, int skipped, List<RowError> errors, List<?> items) {

    public record RowError(int index, String message) {
    }
}
