# Authorization

## `AuthorizationGuard<E>`

Framework-native hook, no Spring Security dependency in the base library — implementations read
whatever `HttpServletRequest` attribute/header/`SecurityContext` your own auth stack already
populated. Opt-in per resource (`RestlessResourceHandler.getAuthorizationGuard()`). The three hook
points most guards actually override:

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

Three more, each defaulting to delegating to one of the above (every existing guard keeps working
unchanged unless it opts in by overriding one):

- `canAccessAfterWrite(action, customActionName, request, after)` — post-image check on
  `update`/`patch`, run *after* the write, inside the same transaction. Catches a transition the
  pre-image `canAccess` alone can't (a client reassigning a row they legitimately own to someone
  else's account). Defaults to `canAccess(action, customActionName, request, after)`.
- `canAccessAll(action, request, entities)` — batched pre-image check for `updateBulk`/
  `deleteAll`, called once against every fetched target instead of looping `canAccess`. Defaults
  to looping `canAccess` (same end result); override to batch against a real policy engine in one
  round trip (`CerbosAuthorizationGuard` does, via one `batch()` RPC).
- `postProcessResponse(action, customActionName, request, entity, dto)` — runs right after
  `Mapper.map(...)` on every single-entity response (`create`/`findOne`/`namedView`/`update`/
  `patch`). Defaults to returning `dto` unchanged. The seam `CerbosAuthorizationGuard` uses to
  auto-mask `@CerbosHiddenField` fields (see below) — a guard for a non-Cerbos auth stack can use
  it for the same kind of field-level response shaping.

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
- `canAccessAll` -> one batched `CerbosBlockingClient.batch(...)` RPC instead of N `check()` calls.
- `scope` -> `CerbosBlockingClient.plan(...)`, translated from Cerbos's query-plan AST into a JPA
  `Specification` by `CerbosQueryPlanTranslator`. An operator/shape the translator doesn't
  recognize is never silently under-restricted: the translator itself always throws on it, and
  `scope()` catches that and fails closed (an always-deny `Specification`, logged at WARN) rather
  than surfacing a 500.

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
see." Uses Cerbos's **output** feature — a CEL expression on a rule computes which field keys to
hide:

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

**Single-entity responses are masked automatically** — `CerbosAuthorizationGuard` overrides
`postProcessResponse` (see `AuthorizationGuard`'s hook points above) to call `CerbosFieldMasker.mask`
after `Mapper.map(...)` on `create`/`findOne`/`namedView`/`update`/`patch`, on its own. No
hand-written `Mapper` needs to call it itself — masking is idempotent, so one that already does
keeps working unaffected. Skips the Cerbos `check()` RPC entirely when the DTO type carries no
`@CerbosHiddenField` field at all; falls back to masking *every* `@CerbosHiddenField` field (fail
closed) if the PDP can't be reached, rather than none.

**List/page responses still need a manual call** — `postProcessResponse` only runs on a
single-entity path. For `findList`/`findPage*`/a custom read action, call `CerbosFieldMasker.maskAll`
from inside that `Mapper`/response path — one batched RPC instead of one per row:

```java
CerbosFieldMasker.maskAll(cerbosClient, principal, "thing", "view", dtos,
        ThingDto::getId, CerbosResourceAttributesMapper.none());
```

If the checked action has no matching policy rule for a principal, nothing is masked — this is a
*refinement* on top of row-level access already granted, not a replacement for it. Pair every
role/attribute combination that should reach the mapper at all with a masking rule.
