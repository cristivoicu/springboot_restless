---
name: spring-boot-restless
description: Scaffold and extend REST resources with the spring-boot-restless framework (entity + DTOs -> generated CRUD/bulk, AuthorizationGuard wiring, Cerbos policy-as-code, write commands, filter DSL). Use when adding a new @RestlessEntity/@RestlessResource, wiring or debugging authorization on one, or extending an existing Restless resource (custom data source, write command, bulk operation, custom filter) in a project that depends on spring-boot-restless.
when_to_use: Triggers on "add an entity/resource", "REST CRUD for <Entity>", "@RestlessEntity", "@RestlessResource", "AuthorizationGuard", "write command"/"custom action" on a Restless resource, "bulk create/update/delete" on one, or a Cerbos policy question tied to this framework.
---

# spring-boot-restless

Annotation-driven REST CRUD for Spring Boot. An entity + four hand-written DTOs is normally the
entire surface area; a compile-time annotation processor generates the repository, a reflective
mapper, and the HTTP resource. Authorization and response shape are the two things this framework
deliberately never generates — they stay hand-written on purpose. Full reference:
[`docs/DEEP_DIVE.md`](../../../docs/DEEP_DIVE.md) at the repo root if present, otherwise the
project's own `README.md`.

## Decision: which tier does this entity need?

```
Does it need entity-specific create/update/delete logic, or a custom read action?
  NO  -> Codeless tier: entity + 4 DTOs only. See "Add an entity: codeless tier" below.
  YES -> Is depending on the `processor` module (compile-time codegen) acceptable?
           YES -> Generated + override: same as codeless, but point one or two
                  @RestlessEntity attributes (createDataSource, authorizationGuard, ...)
                  at a hand-written class. See reference/customization.md.
           NO, or need a write command / named read action / named view
             -> Manual tier: hand-wire a RestlessResourceHandler subclass directly.
                See reference/customization.md "Manual tier".
```

Authorization is never a reason by itself to leave the codeless/generated tier —
`authorizationGuard` can wire a real guard into a generated resource too (see below). What
actually forces the manual tier is entity-specific CUD logic, or needing a custom read action /
write command / named view, none of which `@RestlessEntity` has an attribute for today.

## Add an entity: codeless tier

Write exactly five files, same package, naming convention `{Entity}{Suffix}` — nothing else:

1. **Entity** — `@Entity`, no-arg constructor (Lombok `@NoArgsConstructor`/`@AllArgsConstructor`
   is fine), annotated `@RestlessEntity(basePath = "/things")`.
2. **`{Entity}CreateModel`** implements `CreateModel` — bean-validated input fields for create.
3. **`{Entity}UpdateModel`** implements `UpdateModel` — same idea for full-replace `PUT`.
4. **`{Entity}SearchDto`** extends `AbstractSearchDto` — one field per equality/filter-DSL-suffixed
   filter (see reference/filter-dsl.md).
5. **`{Entity}Dto`** implements `EntityDto` — the response shape. **Always hand-written** — never
   derive this from the entity's own fields blindly; this is the one surface authorization needs
   to control later. Mark a field `@RestlessMapperExclude` if the generated mapper must never
   populate it (not an access-control tool — see `@CerbosHiddenField` for that).

Template:

```java
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@RestlessEntity(basePath = "/things", allowAll = true) // allowAll=true ONLY until a real guard is wired - see below
public class Thing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
}

public class ThingCreateModel implements CreateModel {
    @NotBlank private String name;
}
public class ThingUpdateModel implements UpdateModel {
    @NotBlank private String name;
}
public class ThingSearchDto extends AbstractSearchDto {
    private String name; // equality filter, zero extra code
}
public class ThingDto implements EntityDto {
    private Long id;
    private String name;
}
```

Building (`RestlessEntityProcessor` runs at compile time) generates `ThingRepository`, a
reflective `ThingMapper`, and `ThingRestlessResource`. At startup `RestlessRegistrar` registers
every fixed route live: `POST`/`GET`/`PUT`/`DELETE` by id, `GET list`/`` (page)/`overview`/
`select/async`, plus **unconditional** bulk routes (`POST`/`PUT {basePath}/bulk`,
`POST {basePath}/bulk-delete` — see reference/bulk-and-write-commands.md) and the filter DSL on
`SearchDto` fields, with zero extra code.

**Before this entity is production-ready**, wire a real guard — a resource with no
`AuthorizationGuard` fails to start up unless `allowAll = true` says that's genuinely intended
(startup `IllegalStateException`, not a silently wide-open route). See
reference/authorization.md.

## Not available on the codeless/generated tier

`@RestlessEntity` has **no attribute** for these — needing one means hand-wiring that one
resource on the manual tier (see reference/customization.md):

- Named custom read actions (`getCustomReadActions()`)
- Write commands (`getCustomWriteActions()`) — see reference/bulk-and-write-commands.md
- Named views (`getNamedViews()`)

## Opt-in, one attribute/class away, on any tier

| Want | Do this |
|---|---|
| Partial update (`PATCH`) | `patchDataSource = DefaultPatchDataSource.class` (or hand-written) |
| Optimistic concurrency | Add `@jakarta.persistence.Version` to the entity |
| Soft delete | Implement `SoftDeletable`; `deleteDataSource = DefaultSoftDeleteDataSource.class` |
| Auditing (`createdDate`/`lastModifiedDate`) | Extend `AbstractAuditableEntity` + `@EnableJpaAuditing` on the app |
| API versioning | `version = "1"` on `@RestlessEntity`/`@RestlessResource` |
| Restrict which routes exist | `operations = {...}` (generated tier) / override `getEnabledOperations()` (manual) |
| Embed a related resource inline | `@RestlessEmbed` on a DTO field, client sends `?expand=name` |

## Common pitfalls

- **Boolean `SearchDto` fields must be boxed (`Boolean`), not primitive.** A primitive `boolean`
  can never represent "client didn't send this filter," so the default filter skips primitives
  entirely — a primitive boolean field is silently never filterable.
- **Bulk delete is `POST {basePath}/bulk-delete`, not `DELETE {basePath}` with a body.** RFC 9110
  gives `DELETE` no defined body semantics and proxies/`fetch()` are known to drop it.
- **`Mapper<Entity, Dto>` and `AuthorizationGuard<E>` are never generated, on any tier.** If asked
  to "auto-generate the DTO from the entity," don't — hand-write the DTO shape; the processor can
  still generate the `Mapper` *implementation* once the DTO exists (reflective field-by-field
  copy).
- **A `Class<?>` attribute on `@RestlessEntity` (`authorizationGuard`, `patchDataSource`, ...)
  names one concrete class, not a parameterized type.** A generic guard meant to back more than
  one entity (e.g. `CerbosAuthorizationGuard<E>`) needs a small named `@Component` per entity that
  implements `AuthorizationGuard<Entity>` and delegates to it.
- **`PATCH`/custom write actions/named views are checked against their own `AuthorizationGuard.Action`**
  (`PATCH`, `WRITE_ACTION`, `NAMED_VIEW`) — never inherited from `UPDATE`/`READ_ONE`. Granting one
  doesn't silently grant the other.

## Deeper reference (load on demand)

- [reference/authorization.md](reference/authorization.md) — `AuthorizationGuard`'s three hook
  points, wiring one in, Cerbos-backed policy-as-code, `@CerbosHiddenField` field masking.
- [reference/bulk-and-write-commands.md](reference/bulk-and-write-commands.md) — the three
  unconditional bulk routes, and `WriteAction`/`getCustomWriteActions()` for domain transitions a
  full-replace `PUT` can't guard.
- [reference/filter-dsl.md](reference/filter-dsl.md) — the seven operator suffixes, multi-field
  sort, and the `RestlessSpecifications` escape hatch.
- [reference/customization.md](reference/customization.md) — the generated-tier override
  attributes in full, and the fully-manual tier's shape.
