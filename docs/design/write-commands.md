# Design: write commands (`@RestlessAction` / `WriteAction`)

Status: **implemented** (hand-wired tier — `WriteAction`/`getCustomWriteActions()`,
`POST {basePath}/{id}/actions/{name}`; see `GadgetRestlessResource`'s `rename`
action and `GadgetWriteActionTest` for the worked example). `@RestlessEntity`/
annotation-processor support (the "phase 2" section below) remains open.
Closes the framework's biggest gap against
its own DDD claim: today there is a named-action escape hatch for **reads**
(`ReadAction`/`getCustomReadActions()`) but none for **writes** — every
mutation is CRUD-shaped (`create`/`update`/`patch`/`delete`). There is no way
to express `POST /employees/{id}/promote` without falling back to a
hand-written `@RestController` outside the framework entirely, which forfeits
routing, error-contract, and OpenAPI parity with everything else.

## Why this is the highest-value gap

Microsoft's own DDD/microservices guidance: an anemic model + CRUD is fine for
a genuinely simple service, and becomes an anti-pattern the moment a bounded
context has real, ever-changing business rules. A "promote employee" or
"cancel order" operation carries invariants a `PUT` never can — a client
sending `{"status": "PROMOTED"}` via `update()` can express any transition,
including illegal ones (`TERMINATED` → `PROMOTED`), because full-replace PUT
has no vocabulary for "this is only valid from these prior states." A command
endpoint's whole point is that the transition, not the field, is the unit of
change: the server decides what's legal, the client asks for an outcome.

## Shape: mirror `ReadAction`, not reinvent

The framework already solved "one named extra route beyond the fixed set" for
reads. Writes should look structurally identical, so an entity author who's
already used `getCustomReadActions()` needs to learn nothing new:

```java
public interface WriteAction<E, K, Req, Resp> {

    Class<Req> getRequestType();

    /**
     * Loads by id (like update/delete), applies the transition, returns the
     * response body. Runs inside the same transaction boundary create/updateBulk
     * already get (see RestlessResourceHandler#inTransaction) - a command that
     * mutates two invariant-linked fields must not partially apply.
     */
    Resp execute(E entity, Req request);
}
```

```java
// On RestlessResourceHandler, mirroring getCustomReadActions()/getNamedViews():
public Map<String, WriteAction<E, K, ?, ?>> getCustomWriteActions() {
    return Map.of();
}
```

Registered by `RestlessRegistrar` exactly like custom read actions and named
views are — one extra route per declared name, sharing one dispatch method:

```
POST {basePath}/{id}/actions/{name}
```

(`POST`, not `PATCH`/`PUT`: a command is not idempotent by HTTP's definition
in general — `promote` twice may or may not be a no-op depending on the
domain — so `POST` is the honest default; nothing stops a specific action
from documenting itself as safe to retry, which is exactly what idempotency
keys are for, see below.)

## Authorization

New `AuthorizationGuard.Action.CUSTOM_WRITE`, checked the same two-stage way
update/delete already are:

1. `preCheck(CUSTOM_WRITE, actionName, request)` — coarse, before loading anything.
2. Load the entity via the existing `ReadDataSource`.
3. `canAccess(CUSTOM_WRITE, actionName, request, entity)` — per-instance, using
   the same name-aware overload `NAMED_VIEW` already added to the interface.

This is not new plumbing — `AuthorizationGuard` already has the `String
customActionName` parameter on both `preCheck` and the four-arg `canAccess`
overload specifically because `CUSTOM_READ` needed it first. Write actions
reuse the exact same mechanism, which is a strong argument for doing this
next: the interface doesn't need to change, only `RestlessResourceHandler` and
`RestlessRegistrar` need new dispatch code.

## Request/response DTOs

Two new marker interfaces, `WriteActionRequest`/`WriteActionResponse` (empty,
like `CreateModel`/`UpdateModel` are), so bean validation
(`RestlessResourceHandler#validate`) and Jackson binding work unchanged. A
request with no body at all (e.g. `POST /projects/{id}/archive`) should be
allowed — an empty JSON object `{}` or genuinely no body, resolved the same
way `readBody` already tolerates an empty `PatchModel`.

## Bulk vs. single-item

`ReadAction` filters a collection; `WriteAction` above only covers "one
entity, one command." A bulk command (`POST /employees/actions/give-raise`
with a list of ids + a percentage) is a legitimate second shape, structurally
closer to `deleteAll`'s fail-fast-before-mutating pattern (load and
`canAccess`-check every target before applying any). Recommend shipping the
single-entity shape first (it covers the large majority of real "commands"),
and adding `BulkWriteAction<E, K, Req, Resp>` as a follow-up once the
single-entity dispatch is proven, reusing `inTransaction`'s existing
all-or-nothing wrapping.

## What this does *not* try to solve yet

- **Domain events.** A command's natural companion is "and then publish
  `EmployeePromoted`" — out of scope for the first cut, but the dispatch point
  (`RestlessResourceHandler` right after `WriteAction#execute` returns
  successfully, still inside the transaction) is exactly where an
  `ApplicationEventPublisher.publishEvent(...)` call or a transactional-outbox
  write would go. Worth designing the hook signature now even if the
  publisher wiring comes later, so this isn't a breaking change on top of a
  breaking change.
- **Compile-time (`@RestlessEntity`) support.** Custom read actions
  themselves have no processor support today — only the hand-wired tier can
  declare them. Write actions should follow the same path: prove the shape
  hand-wired first, add a `@RestlessAction` processor attribute once the
  request/response DTO naming convention has actually been used in anger.

## Files touched (estimate)

- `app/.../resource/WriteAction.java` (new interface)
- `app/.../models/WriteActionRequest.java` / `WriteActionResponse.java` (new marker interfaces)
- `app/.../authorization/AuthorizationGuard.java` (new `Action.CUSTOM_WRITE` enum value — additive, not breaking)
- `app/.../resource/RestlessResourceHandler.java` (`getCustomWriteActions()`, a shared `customWrite(HttpServletRequest)` dispatch method, request-body read + validate + load + canAccess + `WriteAction#execute` + response mapping, wrapped in `inTransaction`)
- `app/.../registry/RestlessRegistrar.java` (one route per declared action name, same loop shape as custom read actions)
- `app/.../openapi/RestlessOpenApiCustomizer.java` (document the new routes — same treatment custom read actions already get)
- Tests: a new fixture entity with at least one write action + guard-denial case + validation-failure case + OpenAPI-doc-presence case.

Rough size: comparable to the original custom-read-action feature — a focused
multi-file change, not a rewrite. This is the recommended next thing to build
if the goal is closing the DDD gap, because it reuses more existing machinery
than anything else on this list.
