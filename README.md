# spring-boot-restless

Annotation-driven REST CRUD for Spring Boot, built around DDD and volatility-based design:
each axis of change (persistence, mapping, HTTP wiring, authorization) is hidden behind its own
stable contract, so adding an entity means writing domain-meaningful code once and getting HTTP
for free — not hand-subclassing controllers or hand-writing plumbing DataSource classes. The
actual point of removing that boilerplate is to make room for the thing worth writing by hand
for every action: fine-grained authorization.

## How it works

A `RestlessResourceHandler<E, K>` composes an entity's four `*DataSource` classes (create, read,
update, delete — each its own volatility, independently pluggable) plus its `Mapper`s and
`Specification` builder, and declares concrete, shared handler methods (`create`, `findOne`,
`findList`, `findPage`, `findPageOverview`, `findPageSelect`, `update`, `deleteById`,
`deleteAll`, plus one `customRead` per declared named action). At startup, `RestlessRegistrar`
finds every bean annotated `@RestlessResource`, resolves its entity/id/DTO types via reflection,
and registers each handler method as a live Spring MVC route
(`RequestMappingHandlerMapping.registerMapping`) — the same mechanism Spring Data REST uses. No
code generation, no per-entity controller classes.

**Defaulted away** (still overridable per entity when the default isn't enough):

- **Create/Update/Delete** — `DefaultCreateDataSource`/`DefaultUpdateDataSource`/
  `DefaultDeleteDataSource` (`datasource/defaults/`) copy DTO fields onto the entity via
  `BeanUtils.copyProperties` and save/delete through the repository. Construct one directly in
  your resource bean instead of hand-writing a `*DataSource` subclass — see `Department` below.
- **Search filtering** — `getSpecification()` defaults to an equality predicate on whichever
  `SearchDto` fields are populated (skipping `null`/blank). Override it for anything a plain
  equality match can't express, or add a named **custom read action**
  (`getCustomReadActions()`, its own `SearchDto` + query logic, exposed at
  `GET {basePath}/actions/{name}`) alongside the default — see `Employee`'s `byEmailDomain` action.

**Deliberately not defaulted:**

- **`Mapper<Entity, Dto>`** — always hand-written. A reflective default would silently expose
  whatever fields an entity has, which is exactly the surface authorization needs to control.
- **`AuthorizationGuard<E>`** (`authorization/`) — opt-in per resource via
  `getAuthorizationGuard()`, default-permissive. Three hook points: `preCheck` (coarse, before
  any work), `scope` (an extra `Specification` ANDed onto read queries — row-level visibility),
  `canAccess` (per-instance check once a specific entity is loaded, for single read/update/
  delete). No Spring Security dependency — it's handed the raw `HttpServletRequest`, so it reads
  whatever your own auth stack already populates. See `Employee`'s demonstration guard, scoped
  via an `X-Scope-LastName` header stand-in for a real principal.

## Adding a new entity

Minimal (fully default CUD, default search filter) — using `Department`
(`src/main/java/.../entity/department/`) as the reference, which declares **no** `*DataSource`
classes at all:

1. **Entity** — `Department.java`, a plain `@Entity` with a no-arg constructor.
2. **Repository** — `DepartmentRepository extends SpecificationRepository<Department, Long>`.
3. **DTOs** — `DepartmentDto` (response), `DepartmentCreateModel implements CreateModel`,
   `DepartmentUpdateModel implements UpdateModel`, `DepartmentSearchDto extends
   AbstractSearchDto` (adds filter fields; paging/sorting/delete are inherited/defaulted —
   `DefaultDeleteModel` covers bulk delete without its own DTO).
4. **Mapper** — `DepartmentMapper implements Mapper<Department, DepartmentDto>`.
5. **Read data source** — `DepartmentReadDataSource extends ReadDataSource<...>` (the one verb
   without a default, since read logic is entity-specific by nature).
6. **Resource bean** — `DepartmentRestlessResource extends RestlessResourceHandler<Department, Long>`,
   `@Component @RestlessResource(basePath = "/departments")`, constructing `DefaultCreateDataSource`/
   `DefaultUpdateDataSource`/`DefaultDeleteDataSource` directly in its constructor and wiring the
   read data source + mapper through the `getXDataSource()`/`getXMapper()` accessors. No
   `getSpecification()` override needed either — the default equality filter covers it.

That's it — no `RestlessRegistrar` changes, no new routes to declare by hand. Once the context
starts, `/departments`, `/departments/{id}`, `/departments/list`, `/departments/overview`,
`/departments/select/async` all exist with the standard CRUD verbs.

For custom create/update/delete logic (computed fields, related-entity lookups, ...), hand-write
a `*DataSource` subclass instead of constructing the `Default*` one — see `Employee`'s
`EmployeeCreateDataSource` etc. For a named custom read action or an authorization guard, see
`EmployeeRestlessResource`.

## Project status

This is a working proof of the runtime-registration mechanism (Employee is mounted at
`/employees-dynamic` rather than `/employees` so the original hand-written reference
controllers can stay in place for parity testing — see `ErrorResponseParityTest`). See
`src/test/java/.../registry/` for the tests proving the mechanism: full route coverage,
cross-resource isolation, duplicate-`basePath` detection, error-response parity against the
hand-written baseline, default-CUD end-to-end behavior, custom read actions, and the
authorization guard's four scenarios (unscoped, row-level list scoping, per-instance 403,
fail-fast bulk delete).
