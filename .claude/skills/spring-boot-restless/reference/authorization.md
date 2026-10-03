# Authorization

## `AuthorizationGuard<E>`

Framework-native hook, no Spring Security dependency in the base library — implementations read
whatever `HttpServletRequest` attribute/header/`SecurityContext` your own auth stack already
populated. Opt-in per resource (`RestlessResourceHandler.getAuthorizationGuard()`), three hook
points at different points in the control flow:

```java
public interface AuthorizationGuard<E> {
    // Coarse, before any work. Denial short-circuits 403 before touching the database.
    default boolean preCheck(Action action, String customActionName, HttpServletRequest request);

    // Row-level restriction for read actions: extra Specification ANDed onto the search filter.
    // null means unrestricted.
    default Specification<E> scope(Action action, String customActionName, HttpServletRequest request);

    // Per-instance check once a specific entity is loaded (single read/update/delete/patch/
    // write-action/named-view): "can this principal act on THIS row".
    default boolean canAccess(Action action, HttpServletRequest request, E entity);
}
```

**A resource with no override at all refuses to start up** — `RestlessRegistrar` throws
`IllegalStateException` naming the resource, unless `allowAll = true` on `@RestlessEntity`/
`@RestlessResource` says that's genuinely intended. This is deliberate fail-fast: it turns
"nobody wired a guard" from a silently wide-open route into a startup failure.

Wire one in:
- Generated tier: `@RestlessEntity(authorizationGuard = ThingAuthorizationGuard.class)` — point
  at a hand-written `@Component implements AuthorizationGuard<Thing>`.
- Manual tier: override `getAuthorizationGuard()` on the `RestlessResourceHandler` subclass.

`AuthorizationGuard.allowAll()` (the implicit default when none is overridden) is a shared,
reference-comparable no-op.

**A `Class<?>` attribute can't name a parameterized type.** A guard implementation generic over
many entities (`CerbosAuthorizationGuard<E>` below) needs one small named delegating
`@Component` per entity:

```java
@Component
public class ThingAuthorizationGuardBean implements AuthorizationGuard<Thing> {
    private final CerbosAuthorizationGuard<Thing> delegate;
    public ThingAuthorizationGuardBean(CerbosBlockingClient client) {
        this.delegate = new CerbosAuthorizationGuard<>(client, "thing", Thing::getId,
                CerbosResourceAttributesMapper.reflective(Thing.class));
    }
    // delegate preCheck/scope/canAccess to this.delegate
}
```

## Cerbos-backed authorization (`spring-boot-restless-cerbos`)

`CerbosAuthorizationGuard<E>` implements `AuthorizationGuard<E>` against a real Cerbos PDP —
authorization rules live as policy-as-code (YAML), not Java `if`s.

- `preCheck`/`canAccess` -> `CerbosBlockingClient.check(...)`.
- `scope` -> `CerbosBlockingClient.plan(...)`, translated from Cerbos's query-plan AST into a JPA
  `Specification` by `CerbosQueryPlanTranslator` (fails loud on an unsupported shape, never
  silently under-restricts).

Dependency: `ro.cristivoicu:spring-boot-restless-cerbos` (or import the
`spring-boot-restless-dependencies` BOM and drop versions).

Policy shape (one YAML file per resource kind):

```yaml
apiVersion: api.cerbos.dev/v1
resourcePolicy:
  version: default
  resource: thing
  rules:
    - actions: ["*"]
      effect: EFFECT_ALLOW
      roles: ["admin"]
    - actions: [read_one, read_list, update]
      effect: EFFECT_ALLOW
      roles: ["manager"]
      condition:
        match:
          expr: >
            !has(request.resource.attr.ownerId) ||
            request.resource.attr.ownerId == request.principal.attr.userId
```

The `!has(...) ||` guard matters: `preCheck()` runs against a synthetic, attribute-less resource
(nothing loaded yet) — a bare equality condition would deny every attempt before `scope()`/
`canAccess()` (which run with real attributes) ever get a chance.

Wire the guard:

```java
protected AuthorizationGuard<Thing> getAuthorizationGuard() {
    return new CerbosAuthorizationGuard<>(cerbosClient, "thing", Thing::getId,
            thing -> Map.of("ownerId", AttributeValue.stringValue(thing.getOwnerId())));
}
```

The last argument is a `CerbosResourceAttributesMapper<E>` (entity -> Cerbos attributes, used for
`canAccess`/`scope`). Use `.reflective(Thing.class)` to expose every field automatically, or
`.none()` for a policy that needs no resource attributes.

`CerbosPrincipalResolver` builds the Cerbos `Principal` from whatever `SecurityContextHolder`
already has — special-cases `JwtAuthenticationToken` to expose JWT claims as principal
attributes automatically. For an attribute that isn't a JWT claim (e.g. resolved from the
caller's own DB row), use `CerbosAuthorizationGuard`'s six-argument constructor's
`principalAttributesExtender: Function<HttpServletRequest, Map<String, AttributeValue>>`, merged
additively on top of the JWT-derived principal.

**Fails closed, not open.** A PDP slower than `cerbos.client.timeout` (default `3s`) or
unreachable makes `preCheck`/`canAccess` return `false` and `scope` return an always-deny
`Specification`, logged at WARN. No fail-open switch exists by design.

## `@CerbosHiddenField` — field-level masking

Row-level access answers "can this principal see this row at all." `@CerbosHiddenField` +
`CerbosFieldMasker` answer "which *fields* of an already-visible row should this principal not
see." Deliberately opt-in and hand-called from inside a `Mapper` — never automatic.

Uses Cerbos's **output** feature — a CEL expression on a rule computes which field keys to hide:

```yaml
    - actions: ["view"]
      effect: EFFECT_ALLOW
      roles: ["manager"]
      output:
        when:
          ruleActivated: |-
            request.principal.attr.canViewSalary == true ? [] : ["salary"]
```

```java
public class ThingDto implements EntityDto {
    @CerbosHiddenField
    private BigDecimal salary;
}
```

```java
// Single entity:
CerbosFieldMasker.mask(cerbosClient, principal, resource, "view", dto);
// A list - one batched RPC instead of N:
CerbosFieldMasker.maskAll(cerbosClient, principal, "thing", "view", dtos,
        ThingDto::getId, CerbosResourceAttributesMapper.none());
```

If the checked action has no matching policy rule for a principal, nothing is masked — this is a
*refinement* on top of row-level access already granted, not a replacement for it. Pair every
role/attribute combination that should reach the mapper at all with a masking rule.
