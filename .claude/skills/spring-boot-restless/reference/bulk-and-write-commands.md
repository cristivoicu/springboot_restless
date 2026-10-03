# Bulk operations and write commands

## Bulk operations — unconditional, no opt-in

Every resource, any tier, gets three bulk routes for free — they ride on the same
`CreateDataSource`/`UpdateDataSource`/`DeleteDataSource` every resource already has:

| Verb | Route | Body | Response |
|---|---|---|---|
| `POST` | `{basePath}/bulk` | JSON array of `CreateModel` | `200`, array of created DTOs |
| `PUT` | `{basePath}/bulk` | JSON object keyed by id — `{"1": {...}, "2": {...}}` | `200`, array of updated DTOs |
| `POST` | `{basePath}/bulk-delete` | `{"ids": ["1", "2", "3"]}` | `204 No Content` |

The only way to remove them is excluding `CREATE`/`UPDATE`/`DELETE_ALL` from `operations`/
`getEnabledOperations()` entirely — that removes the single-item route too, they're not
independently gate-able.

**All-or-nothing, not best-effort.** Every bulk write is one real transaction. Bulk update/delete
additionally load and `canAccess`-check **every** target before writing anything — a batch never
partially applies because item #7 of 10 was denied. Bulk create is the one exception with no
pre-write guard loop (`preCheck(CREATE, ...)` only, once — none of the rows exist yet for a
per-instance check).

Override `CreateDataSource#createAll`/`UpdateDataSource#updateAll` directly (they default to a
plain loop over the single-item verb) only for a genuinely different bulk strategy (a single
batched `saveAll(...)`, say) — a hand-written single-item `*DataSource` already gets correct bulk
behavior for free otherwise.

**When not to reach for it:** very large/unreliable-source batches (one bad row aborts
everything — pre-validate or chunk instead), a mutation that isn't a plain field-level replace
(see write commands below — no `BulkWriteAction` exists), or high-volume streaming ingestion
(every id is individually loaded/checked, fine for hundreds-to-low-thousands per request).

## Write commands — `WriteAction<E, Req, Resp>`

For a transition a full-replace `PUT` has no vocabulary to guard (illegal-state prevention,
multi-step domain logic, a cap that depends on the row's *current* state, append-only mutation of
a collection the update model deliberately never exposes). **Hand-wired tier only** —
`@RestlessEntity` has no attribute for this.

```
POST {basePath}/{id}/actions/{name}
```

(`POST`, not `PUT`/`PATCH`: not idempotent by HTTP's definition in general.)

```java
public interface WriteAction<E, Req extends WriteActionRequest, Resp> {
    Class<Req> getRequestType();
    Class<Resp> getResponseType();
    // Runs against an already-loaded, already-guard-checked entity and validated request body,
    // inside one real transaction.
    Resp execute(E entity, Req request) throws Exception;
}
```

```java
@Override
public Map<String, WriteAction<Thing, ?, ?>> getCustomWriteActions() {
    return Map.of(
            "activate", new WriteAction<Thing, ActivateRequest, ThingDto>() {
                @Override public Class<ActivateRequest> getRequestType() { return ActivateRequest.class; }
                @Override public Class<ThingDto> getResponseType() { return ThingDto.class; }

                @Override
                public ThingDto execute(Thing entity, ActivateRequest request) {
                    if (entity.getStatus() != Status.PENDING) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Only a PENDING thing can be activated, was " + entity.getStatus());
                    }
                    entity.setStatus(Status.ACTIVE);
                    return mapper.map(repository.save(entity));
                }
            }
    );
}
```

Request flow: `preCheck(WRITE_ACTION, "activate")` -> read+validate request body -> open
transaction -> load entity -> `canAccess(WRITE_ACTION, "activate", entity)` -> `execute(...)` ->
commit -> `200 OK`. A guard denial or a `ResponseStatusException` thrown from `execute` rolls the
whole transaction back — illegal-transition prevention needs no other machinery.

`execute` skips `Mapper` entirely, both directions — `Req`/`Resp` are whatever shape the action
author decides. Nothing stops `execute` from calling the resource's own `Mapper` internally to
build `Resp` (the example above does) — that's just an implementation detail.

**Request DTO**: a plain bean-validated POJO implementing the empty `WriteActionRequest` marker
interface (same idiom as `CreateModel`/`UpdateModel`).

**Use it for:** a transition with a real invariant, a business-rule cap depending on current
state, append-only mutation of a hidden collection. **Don't use it for:** a plain field-level
replace (that's `PUT`/`PATCH`), or bulk (no `BulkWriteAction` exists — one call is one entity).
