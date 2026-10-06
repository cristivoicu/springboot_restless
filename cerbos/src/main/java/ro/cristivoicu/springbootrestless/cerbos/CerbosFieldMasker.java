package ro.cristivoicu.springbootrestless.cerbos;

import com.google.protobuf.Value;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResourcesResult;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import dev.cerbos.sdk.builders.ResourceAction;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Nulls out whichever {@link CerbosHiddenField}-annotated fields of a DTO a Cerbos policy says to
 * hide, using Cerbos's <b>output</b> feature: a resource policy rule can attach an arbitrary
 * CEL-computed value to its decision, evaluated only when that rule activates ({@code
 * output.when.ruleActivated} in the policy YAML). Modelling "which fields to hide" as an output
 * that's a CEL list of field keys turns "hide salary for managers who can't view it" into one
 * line of policy instead of a branch inside every {@code Mapper}:
 * <pre>{@code
 * - actions: ["view"]
 *   effect: EFFECT_ALLOW
 *   roles: ["manager"]
 *   output:
 *     when:
 *       ruleActivated: |-
 *         request.principal.attr.canViewSalary == true ? [] : ["salary"]
 * }</pre>
 * <p>
 * Called by hand inside a {@code Mapper.map(...)} implementation, after building the DTO - see
 * {@code EmployeeMapper} in the {@code example} module for a worked example, including {@link
 * #maskAll} for list/page reads (one batched RPC instead of one per row).
 * <p>
 * <b>Fail-open on a missing rule.</b> If the checked action has no matching policy rule for a
 * principal at all, {@code getOutputs()} is simply empty and nothing gets masked - this is a
 * field-level <em>refinement</em> layered on top of the row-level access {@code
 * AuthorizationGuard.preCheck}/{@code canAccess}/{@code scope} already granted, not a replacement
 * for them. Always pair a {@code view} (or whichever action name is used here) rule with every
 * role/attribute combination that should reach this mapper at all.
 */
public final class CerbosFieldMasker {

    private CerbosFieldMasker() {
    }

    /**
     * Core masking logic, working directly off an outputs map ({@code
     * CheckResult.getOutputs().asMap()}) rather than the SDK's {@code CheckResult}/{@code
     * Outputs} types - both have package-private constructors and can't be hand-built outside
     * {@code dev.cerbos.sdk}, but {@code Value} is plain public protobuf, so this is the layer
     * that stays unit-testable without a running PDP.
     */
    public static <D> D mask(java.util.Map<String, Value> outputs, D dto) {
        Set<String> hiddenFieldKeys = new LinkedHashSet<>();
        outputs.values().forEach(value -> collectStrings(value, hiddenFieldKeys));
        applyMask(dto, hiddenFieldKeys);
        return dto;
    }

    public static <D> D mask(CheckResult result, D dto) {
        return mask(result.getOutputs().asMap(), dto);
    }

    /** Single entity: one {@code check()} RPC. */
    public static <D> D mask(CerbosBlockingClient client, Principal principal, Resource resource, String action, D dto) {
        return mask(client.check(principal, resource, action), dto);
    }

    /**
     * Many entities: one <b>batched</b> RPC ({@code client.batch(...)}) instead of one {@code
     * check()} per row - the natural fit for masking every row of a list/page read. {@code
     * attributesMapper} builds each DTO's resource attributes the same way {@link
     * CerbosAuthorizationGuard} does for row-level checks (see {@link
     * CerbosResourceAttributesMapper}); pass {@link CerbosResourceAttributesMapper#none()} if the
     * policy's output condition never depends on resource attributes (e.g. principal-attribute-only,
     * like the {@code canViewSalary} example above).
     */
    public static <D> List<D> maskAll(CerbosBlockingClient client, Principal principal, String resourceKind,
                                       String action, List<D> dtos, Function<D, ?> idExtractor,
                                       CerbosResourceAttributesMapper<D> attributesMapper) {
        if (dtos.isEmpty()) {
            return dtos;
        }

        ResourceAction[] resourceActions = dtos.stream()
                .map(dto -> {
                    ResourceAction resourceAction = ResourceAction
                            .newInstance(resourceKind, String.valueOf(idExtractor.apply(dto)))
                            .withActions(action);
                    var attributes = attributesMapper.attributesOf(dto);
                    return attributes.isEmpty() ? resourceAction : resourceAction.withAttributes(attributes);
                })
                .toArray(ResourceAction[]::new);

        CheckResourcesResult result = client.batch(principal).addResources(resourceActions).check();

        for (D dto : dtos) {
            String id = String.valueOf(idExtractor.apply(dto));
            result.find(id).ifPresent(checkResult -> mask(checkResult, dto));
        }
        return dtos;
    }

    /**
     * Whether {@code type} carries any {@link CerbosHiddenField}-annotated field at all - the
     * cheap, reflection-only check {@link CerbosAuthorizationGuard#postProcessResponse} runs
     * first, so a DTO that never uses field masking costs nothing extra (no Cerbos {@code
     * check()} RPC) on every single-entity response.
     */
    static boolean hasAnyHiddenField(Class<?> type) {
        return CerbosReflection.declaredFieldsOf(type).stream()
                .anyMatch(field -> field.isAnnotationPresent(CerbosHiddenField.class));
    }

    /**
     * Unconditionally nulls out every {@link CerbosHiddenField}-annotated field, bypassing the
     * outputs-based selection {@link #mask} otherwise uses - for {@link
     * CerbosAuthorizationGuard#postProcessResponse}'s own fail-closed fallback when the PDP can't
     * be reached to say which fields (if any) should actually be hidden. Revealing every
     * {@code @CerbosHiddenField} field by default in that situation would be fail-<em>open</em>;
     * hiding all of them is the fail-closed choice consistent with this class's sibling guard
     * methods.
     */
    static <D> D maskAllHiddenFields(D dto) {
        Set<String> allKeys = new LinkedHashSet<>();
        for (Field field : CerbosReflection.declaredFieldsOf(dto.getClass())) {
            CerbosHiddenField annotation = field.getAnnotation(CerbosHiddenField.class);
            if (annotation != null) {
                allKeys.add(annotation.value().isEmpty() ? field.getName() : annotation.value());
            }
        }
        applyMask(dto, allKeys);
        return dto;
    }

    private static void collectStrings(Value value, Set<String> into) {
        switch (value.getKindCase()) {
            case STRING_VALUE -> into.add(value.getStringValue());
            case LIST_VALUE -> value.getListValue().getValuesList().forEach(v -> collectStrings(v, into));
            default -> {
                // Not a string or a list of them - not a field key this masker understands.
                // Ignored, not failed: an output can carry other things (audit metadata, ...)
                // unrelated to field masking.
            }
        }
    }

    private static void applyMask(Object dto, Set<String> hiddenFieldKeys) {
        if (hiddenFieldKeys.isEmpty()) {
            return;
        }
        for (Field field : CerbosReflection.declaredFieldsOf(dto.getClass())) {
            CerbosHiddenField annotation = field.getAnnotation(CerbosHiddenField.class);
            if (annotation == null) {
                continue;
            }
            String key = annotation.value().isEmpty() ? field.getName() : annotation.value();
            if (!hiddenFieldKeys.contains(key)) {
                continue;
            }
            if (field.getType().isPrimitive()) {
                throw new IllegalStateException("@CerbosHiddenField on primitive field " + field
                        + " can't be masked (no null to fall back to) - use a boxed/reference type instead");
            }
            try {
                field.set(dto, null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not mask " + field, e);
            }
        }
    }
}
