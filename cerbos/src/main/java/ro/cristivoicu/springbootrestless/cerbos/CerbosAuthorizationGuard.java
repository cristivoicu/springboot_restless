package ro.cristivoicu.springbootrestless.cerbos;

import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression.Operand;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosException;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import ro.cristivoicu.springbootrestless.authorization.AuthorizationGuard;
import ro.cristivoicu.springbootrestless.mapper.Mapper;

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
 * <b>Attributes the JWT itself doesn't carry.</b> {@link CerbosPrincipalResolver} only ever
 * exposes what's already sitting in the token's claims. Some conditions need a principal
 * attribute that has to be looked up from somewhere else instead - "which department does this
 * user belong to" resolved from a database row keyed by their email, say, rather than a claim
 * Keycloak was configured to emit. {@code principalAttributesExtender} (six-argument
 * constructor) is called with the current request after the JWT-derived principal is built, and
 * whatever it returns is merged on top via {@link Principal#withAttributes} - additively, so it
 * can add attributes without disturbing the ones {@link CerbosPrincipalResolver} already
 * resolved. Defaults to contributing nothing.
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
 * <p>
 * <b>Fails closed, not open.</b> A PDP that's slow past {@code cerbos.client.timeout} (see
 * {@link CerbosClientConfiguration}) or unreachable makes every one of these three methods throw
 * {@link CerbosException} deep inside the SDK - left uncaught, that would surface as an
 * undifferentiated 500 from whatever generic exception handling happens to be configured (or none
 * at all). Instead, every call here is wrapped: {@link #preCheck}/{@link #canAccess} return {@code
 * false} (denied) and {@link #scope} returns the same always-deny {@link Specification} its
 * {@code ALWAYS_DENIED} branch already uses - a PDP outage degrades to "nobody can do anything
 * through this guard" rather than either an opaque crash or, worse, silently falling through to
 * unrestricted access. There's no fail-open escape hatch on this class by design: an
 * authorization check that can't reach its policy source has no basis to say yes.
 *
 * @param <E> the entity type this guard protects
 */
public class CerbosAuthorizationGuard<E> implements AuthorizationGuard<E> {

    private static final Logger log = LoggerFactory.getLogger(CerbosAuthorizationGuard.class);

    private final CerbosBlockingClient client;
    private final String resourceKind;
    private final Function<E, ?> idExtractor;
    private final CerbosResourceAttributesMapper<E> attributesMapper;
    private final Mapper<E, ?> mapper;
    private final CerbosDtoResourceAttributesMapper dtoAttributesMapper;
    private final BiFunction<Action, String, String> actionNaming;
    private final Function<HttpServletRequest, Map<String, AttributeValue>> principalAttributesExtender;

    public CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                     CerbosResourceAttributesMapper<E> attributesMapper) {
        this(client, resourceKind, idExtractor, attributesMapper, CerbosActionNaming.DEFAULT);
    }

    public CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                     CerbosResourceAttributesMapper<E> attributesMapper,
                                     BiFunction<Action, String, String> actionNaming) {
        this(client, resourceKind, idExtractor, attributesMapper, actionNaming, request -> Map.of());
    }

    public CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                     CerbosResourceAttributesMapper<E> attributesMapper,
                                     BiFunction<Action, String, String> actionNaming,
                                     Function<HttpServletRequest, Map<String, AttributeValue>> principalAttributesExtender) {
        this.client = client;
        this.resourceKind = resourceKind;
        this.idExtractor = idExtractor;
        this.attributesMapper = attributesMapper;
        this.mapper = null;
        this.dtoAttributesMapper = null;
        this.actionNaming = actionNaming;
        this.principalAttributesExtender = principalAttributesExtender;
    }

    /**
     * DTO-aware sibling of the entity-only constructors above - for a policy condition that needs
     * a computed attribute only the response DTO carries, not the entity (see {@link
     * CerbosDtoResourceAttributesMapper}'s own javadoc). {@code <D>} is this constructor's own type
     * parameter, distinct from the class's {@code <E>} - deliberately, so {@code
     * CerbosAuthorizationGuard<E>} stays a drop-in field/variable type everywhere it's already
     * declared (e.g. {@code ProjectAuthorizationGuardBean}), unaffected by which constructor a
     * particular instance happens to have been built with.
     */
    public <D> CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                         Mapper<E, D> mapper, CerbosDtoResourceAttributesMapper<E, D> dtoAttributesMapper) {
        this(client, resourceKind, idExtractor, mapper, dtoAttributesMapper, CerbosActionNaming.DEFAULT);
    }

    public <D> CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                         Mapper<E, D> mapper, CerbosDtoResourceAttributesMapper<E, D> dtoAttributesMapper,
                                         BiFunction<Action, String, String> actionNaming) {
        this(client, resourceKind, idExtractor, mapper, dtoAttributesMapper, actionNaming, request -> Map.of());
    }

    public <D> CerbosAuthorizationGuard(CerbosBlockingClient client, String resourceKind, Function<E, ?> idExtractor,
                                         Mapper<E, D> mapper, CerbosDtoResourceAttributesMapper<E, D> dtoAttributesMapper,
                                         BiFunction<Action, String, String> actionNaming,
                                         Function<HttpServletRequest, Map<String, AttributeValue>> principalAttributesExtender) {
        this.client = client;
        this.resourceKind = resourceKind;
        this.idExtractor = idExtractor;
        this.attributesMapper = null;
        this.mapper = mapper;
        this.dtoAttributesMapper = dtoAttributesMapper;
        this.actionNaming = actionNaming;
        this.principalAttributesExtender = principalAttributesExtender;
    }

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        String cerbosAction = actionNaming.apply(action, customActionName);
        Principal principal = principalOf(request);
        Resource resource = Resource.newInstance(resourceKind, "new");
        try {
            return client.check(principal, resource, cerbosAction).isAllowed(cerbosAction);
        } catch (CerbosException e) {
            logFailedClosed("preCheck", cerbosAction, e);
            return false;
        }
    }

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, E entity) {
        return canAccess(action, null, request, entity);
    }

    /**
     * The name-aware overload {@link AuthorizationGuard#canAccess(Action, String, HttpServletRequest, Object)}
     * added for {@link Action#NAMED_VIEW} - forwards {@code customActionName} into {@code
     * actionNaming} the same way {@link #preCheck}/{@link #scope} already do, so a policy can
     * grant/deny each named view independently (e.g. {@code view_billing} vs {@code
     * view_fulfillment}) rather than every named view sharing one {@code NAMED_VIEW} decision.
     */
    @Override
    public boolean canAccess(Action action, String customActionName, HttpServletRequest request, E entity) {
        String cerbosAction = actionNaming.apply(action, customActionName);
        Principal principal = principalOf(request);
        Resource resource = resourceOf(entity);
        try {
            return client.check(principal, resource, cerbosAction).isAllowed(cerbosAction);
        } catch (CerbosException e) {
            logFailedClosed("canAccess", cerbosAction, e);
            return false;
        }
    }

    @Override
    public Specification<E> scope(Action action, String customActionName, HttpServletRequest request) {
        String cerbosAction = actionNaming.apply(action, customActionName);
        Principal principal = principalOf(request);
        Resource resource = Resource.newInstance(resourceKind);

        PlanResourcesResult plan;
        try {
            plan = client.plan(principal, resource, cerbosAction);
        } catch (CerbosException e) {
            logFailedClosed("scope", cerbosAction, e);
            return (root, query, cb) -> cb.disjunction();
        }
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

    private void logFailedClosed(String hook, String cerbosAction, CerbosException e) {
        log.warn("Cerbos PDP unreachable/errored during {}('{}') on resource kind '{}' "
                        + "(gRPC status {}) - failing closed (denied)",
                hook, cerbosAction, resourceKind, e.getStatusCode(), e);
    }

    private Principal principalOf(HttpServletRequest request) {
        Principal principal = CerbosPrincipalResolver.resolve(request);
        Map<String, AttributeValue> extra = principalAttributesExtender.apply(request);
        return extra.isEmpty() ? principal : principal.withAttributes(extra);
    }

    private Resource resourceOf(E entity) {
        Object id = idExtractor.apply(entity);
        Resource resource = Resource.newInstance(resourceKind, String.valueOf(id));
        Map<String, AttributeValue> attributes = attributesMapper != null
                ? attributesMapper.attributesOf(entity)
                : dtoAttributesOf(entity);
        return attributes.isEmpty() ? resource : resource.withAttributes(attributes);
    }

    /** {@code mapper}/{@code dtoAttributesMapper} are only ever both non-null or both null - set together by the DTO-aware constructors, {@link #resourceOf}'s own {@code attributesMapper != null} branch is what guarantees this is only reached when they are. */
    @SuppressWarnings("unchecked")
    private Map<String, AttributeValue> dtoAttributesOf(E entity) {
        Object dto = mapper.map(entity);
        return dtoAttributesMapper.attributesOf(entity, dto);
    }
}
