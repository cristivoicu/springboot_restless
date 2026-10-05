package ro.cristivoicu.springbootrestless.error;

/**
 * One bean-validation violation inside {@code RestlessExceptionHandler}'s {@code errors} array -
 * {@code field} (the property name), {@code message} (the violation's default message), and
 * {@code code} (the constraint's short name, e.g. {@code "NotBlank"}/{@code "Max"} - {@link
 * org.springframework.validation.FieldError#getCode()} verbatim). A plain record, not a nested
 * class: this is wire-shape only, serialized by Jackson as a plain JSON object - no behavior of
 * its own.
 * <p>
 * Replaces the previous {@code "field: message"} string shape - a client parsing {@code errors}
 * programmatically (not just displaying it) had no reliable way to separate the field name from
 * the message, or to key off the violated constraint at all. See the Changelog's "Changed"
 * section: this is a breaking wire-format change for any existing consumer.
 */
public record RestlessFieldError(String field, String message, String code) {
}
