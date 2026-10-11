package ro.cristivoicu.springbootrestless.models;

/**
 * Bean Validation groups for a model shared between verbs. Restless validates every create body
 * with {@code Default} + {@link OnCreate}, every update body with {@code Default} +
 * {@link OnUpdate}, every patch body with {@code Default} + {@link OnPatch}:
 * <pre>{@code
 * public record EventRequest(
 *         @NotBlank String name,
 *         @NotNull(groups = OnCreate.class) String type,   // required to create, optional to update
 *         ...) implements CreateModel, UpdateModel {}
 * }</pre>
 * A constraint with no {@code groups} keeps applying everywhere, exactly as before.
 */
public final class RestlessValidationGroups {

    private RestlessValidationGroups() {
    }

    public interface OnCreate {
    }

    public interface OnUpdate {
    }

    public interface OnPatch {
    }
}
