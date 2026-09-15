package ro.cristivoicu.springbootrestless.models;

/**
 * Marker for a {@code {Entity}PatchModel} - the naming-convention DTO a {@code PatchDataSource}
 * consumes. Unlike {@link UpdateModel} (expected to carry every field, validated with {@code
 * @NotBlank} etc.), a patch model's fields are all optional: {@code null} means "leave this field
 * alone", not "clear it" - see {@link ro.cristivoicu.springbootrestless.controller.patch.PatchDataSource}'s
 * javadoc.
 */
public interface PatchModel {
}
