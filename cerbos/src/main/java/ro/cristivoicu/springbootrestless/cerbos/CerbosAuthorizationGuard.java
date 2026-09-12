package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression.Operand;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link AuthorizationGuard} backed by a real Cerbos PDP. An entity author wires one in by
 * overriding {@code RestlessResourceHandler.getAuthorizationGuard()}:
 * <pre>{@code
 * protected AuthorizationGuard<Employee> getAuthorizationGuard() {
 *     return new CerbosAuthorizationGuard<>(cerbosClient, "employee",
 *             Employee::getId,
 *             employee -> Map.of("lastName", AttributeValue.stringValue(employee.getLastName())));
 * }
 * }</pre>
 * <p>
 * All three {@link AuthorizationGuard} hooks map onto one Cerbos concept each:
 * <ul>
 *     <li>{@link #preCheck} - {@code CerbosBlockingClient.check(...)} against a resource
 *     instance with no id yet (nothing has been loaded/created), i.e. "can this principal even
 *     attempt this action on this resource kind".</li>
 *     <li>{@link #canAccess} - the same {@code check(...)}, now against the loaded entity's own
 *     id and attributes (from {@link CerbosResourceAttributesMapper}).</li>
 *     <li>{@link #scope} - {@code CerbosBlockingClient.plan(...)} (Cerbos's "query plan"
 *     feature), translated into a JPA {@link Specification} by {@link
 *     CerbosQueryPlanTranslator}. {@code ALWAYS_ALLOWED} needs no extra restriction ({@code null},
 *     same as an unscoped guard); {@code ALWAYS_DENIED} becomes a specification matching no rows
 *     at all; {@code CONDITIONAL} is translated.</li>
 * </ul>
 * The action name a policy checks against comes from {@code actionNaming} - defaults to {@link
 * CerbosActionNaming#DEFAULT}, override-able (e.g. a differently-named existing policy) via the
 * five-argument constructor.
 * <p>
 * <b>Writing conditions that reference resource attributes.</b> {@link #preCheck}'s resource is
 * synthetic - a placeholder id, no attributes at all - since it runs before any entity is loaded
 * (or, for {@code CREATE}, before one even exists). A policy condition like {@code
 * request.resource.attr.ownerId == request.principal.attr.userId} evaluates to {@code false}
 * against that empty resource, which would make {@code preCheck} deny the attempt outright for
 * every principal the condition applies to - before {@link #scope}/{@link #canAccess} (which
 * always run against the real, fully-attributed entity or query plan) ever get a chance to do the
 * actual restriction. Guard such conditions with CEL's {@code has()}: {@code
 * !has(request.resource.attr.ownerId) || request.resource.attr.ownerId ==
 * request.principal.attr.userId} - absent (the coarse pre-check) passes through, present (the
 * real check) is actually compared. See {@code policies/employee.yaml} in the {@code example}
 * module for a worked example.
 *
 * @param <E> the entity type this guard protects
 */
public class CerbosAuthorizationGuard<E> implements AuthorizationGuard<E> {

    private final CerbosBlockingClient client;
    private final String resourceKind;
    private final Function<E, ?> idExtractor;
    private final CerbosResourceAttributesMapper<E> attributesMapper;
    private final BiFunction<Action, String, String> actionNaming;

    public CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                     CerbosResourceAttributesMapper<E> attributesMapper) {
        this(client, resourceKind, idExtractor, attributesMapper, CerbosActionNaming.DEFAULT);
    }

    public CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                     CerbosResourceAttributesMapper<E> attributesMapper,
                                     BiFunction<Action, String, String> actionNaming) {
        this.client = client;
        this.resourceKind = resourceKind;
        this.idExtractor = idExtractor;
        this.attributesMapper = attributesMapper;
        this.actionNaming = actionNaming;
    }

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        String cerbosAction = actionNaming.apply(action, customActionName);
        Principal principal = CerbosPrincipalResolver.resolve(request);
        Resource resource = Resource.newInstance(resourceKind, "new");
        return client.check(principal, resource, cerbosAction).isAllowed(cerbosAction);
    }

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, E entity) {
        String cerbosAction = actionNaming.apply(action, null);
        Principal principal = CerbosPrincipalResolver.resolve(request);
        Resource resource = resourceOf(entity);
        return client.check(principal, resource, cerbosAction).isAllowed(cerbosAction);
    }

    @Override
    public Specification<E> scope(Action action, String customActionName, HttpServletRequest request) {
        String cerbosAction = actionNaming.apply(action, customActionName);
        Principal principal = CerbosPrincipalResolver.resolve(request);
        Resource resource = Resource.newInstance(resourceKind);

        PlanResourcesResult plan = client.plan(principal, resource, cerbosAction);
        if (plan.isAlwaysAllowed()) {
            return null;
        }
        if (plan.isAlwaysDenied()) {
            return (root, query, cb) -> cb.disjunction();
        }

        Operand condition = plan.getCondition().orElseThrow(() -> new IllegalStateException(
                "Cerbos plan for action '" + cerbosAction + "' on resource kind '" + resourceKind
                        + "' is CONDITIONAL but carries no condition"));
        return CerbosQueryPlanTranslator.translate(condition);
    }

    private Resource resourceOf(E entity) {
        Object id = idExtractor.apply(entity);
        Resource resource = Resource.newInstance(resourceKind, String.valueOf(id));
        Map<String, AttributeValue> attributes = attributesMapper.attributesOf(entity);
        return attributes.isEmpty() ? resource : resource.withAttributes(attributes);
    }
}
