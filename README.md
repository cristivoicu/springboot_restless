# spring-boot-restless

Annotation-driven REST CRUD for Spring Boot, built around DDD and volatility-based design:
each axis of change (persistence, mapping, HTTP wiring, authorization) is hidden behind its own
stable contract, so adding an entity means writing domain-meaningful code once and getting HTTP
for free — not hand-subclassing controllers or hand-writing plumbing classes. The actual point
of removing that boilerplate is to make room for the thing worth writing by hand for every
action: fine-grained authorization.

## Modules

- **`processor`** — the `@RestlessEntity` annotation and the compile-time processor that
  generates a `{Entity}RestlessResource` glue class from it (see below). A separately-built
  artifact from `app` on purpose: an annotation processor can't process its own compilation unit.
- **`app`** — the framework itself (`RestlessResourceHandler`, `RestlessRegistrar`, the
  `Default*DataSource` classes, `AuthorizationGuard`, ...) and nothing else — a library, not a
  runnable application. Ships no entities and no `@SpringBootApplication` class; it's
  self-tested against small, generically-named, test-scoped fixtures (`Gadget`/`Gizmo`/
  `Sprocket` under `app/src/test/.../fixtures/`) that never leave the test jar, so the framework
  stays independently verifiable without depending on `example` existing or staying in sync.
- **`example`** — a standalone runnable Spring Boot application that consumes `app` and
  `processor` as a real external project would (see `ExampleApplication`), demonstrating
  realistic usage with the original `Employee`/`Department`/`Project` entities under its own
  `ro.cristivoicu.springbootrestless.example` package.

## How it works

A `RestlessResourceHandler<E, K>` composes an entity's four `*DataSource` classes (create, read,
update, delete — each its own volatility, independently pluggable) plus its `Mapper`s and
`Specification` builder, and declares concrete, shared handler methods (`create`, `findOne`,
`findList`, `findPage`, `findPageOverview`, `findPageSelect`, `update`, `deleteById`,
`deleteAll`, plus one `customRead` per declared named action). At startup, `RestlessRegistrar`
finds every bean annotated `@RestlessResource`, resolves its entity/id/DTO types via reflection,
and registers each handler method as a live Spring MVC route
(`RequestMappingHandlerMapping.registerMapping`) — the same mechanism Spring Data REST uses.

**Defaulted away** (still overridable per entity when the default isn't enough):

- **Create/Update/Delete/Read** — `DefaultCreateDataSource`/`DefaultUpdateDataSource`/
  `DefaultDeleteDataSource`/`DefaultReadDataSource` (`datasource/defaults/`) copy DTO fields onto
  the entity via `BeanUtils.copyProperties` (create/update) or plain-delegate to the repository
  (read/delete) — read logic turned out to have no entity-specific behavior in practice, only
  the search *filter* does (see below).
- **Search filtering** — `getSpecification()` defaults to an equality predicate on whichever
  `SearchDto` fields are populated (skipping `null`/blank). Override it for anything a plain
  equality match can't express, or add a named **custom read action**
  (`getCustomReadActions()`, its own `SearchDto` + query logic, exposed at
  `GET {basePath}/actions/{name}`) alongside the default.
- **The `{Entity}RestlessResource` glue class itself** — see "Adding a new entity" below.

**Deliberately not defaulted:**

- **`Mapper<Entity, Dto>`** — always hand-written. A reflective default would silently expose
  whatever fields an entity has, which is exactly the surface authorization needs to control.
- **`AuthorizationGuard<E>`** (`authorization/`) — opt-in per resource via
  `getAuthorizationGuard()`, default-permissive. Three hook points: `preCheck` (coarse, before
  any work), `scope` (an extra `Specification` ANDed onto read queries — row-level visibility),
  `canAccess` (per-instance check once a specific entity is loaded, for single read/update/
  delete). No Spring Security dependency — it's handed the raw `HttpServletRequest`, so it reads
  whatever your own auth stack already populates.

## Adding a new entity

Three approaches, all demonstrated in `example` (`example/src/main/java/.../example/entity/`):

### Simplest — compile-time generated (`Project`)

Write the entity + DTOs + `Mapper`, following the naming convention; everything else is
generated at compile time by `RestlessEntityProcessor` (see `target/generated-sources/annotations`
after a build):

1. **Entity** — `Project.java`, `@Entity` with a no-arg constructor, annotated
   `@RestlessEntity(basePath = "/projects")`.
2. **DTOs + Mapper** — `ProjectCreateModel`, `ProjectUpdateModel`, `ProjectSearchDto`,
   `ProjectDto`, `ProjectMapper`, all in the same package, named exactly `{Entity}{Suffix}`.

That's it. `ProjectRepository` (missing) and `ProjectRestlessResource` are both generated.

For logic a generated default can't express, hand-write a class and point the annotation at it
instead of relying on the naming convention — every attribute defaults to "use the convention/
generated default":

```java
@RestlessEntity(basePath = "/projects", createDataSource = ProjectCreateDataSource.class)
```

`createModel`/`updateModel`/`searchDto`/`mapper` work the same way for DTOs that don't follow
the naming convention. See `Project`'s `ProjectCreateDataSource` (defaults a blank description)
for a complete example — the generated resource injects it as a constructor parameter, exactly
like a hand-written resource bean would.

### Manual — runtime defaults, no codegen (`Department`)

Same generated-default behavior, wired by hand instead of by the processor — useful for
understanding what the generated code actually does, or if you'd rather not add the `processor`
module dependency:

1. **Entity + Repository + DTOs + Mapper** — same as above, plus
   `DepartmentRepository extends SpecificationRepository<Department, Long>` by hand.
2. **Resource bean** — `DepartmentRestlessResource extends RestlessResourceHandler<Department, Long>`,
   `@Component @RestlessResource(basePath = "/departments")`, constructing all four
   `Default*DataSource` instances directly in its constructor.

### Full manual (`Employee`)

Hand-written `*DataSource` classes for entity-specific create/update/delete logic, a named
custom read action, and an authorization guard — see `EmployeeRestlessResource`. Also keeps the
original hand-written `@RestController` classes at `/employees` (vs. `/employees-dynamic` for
the dynamic route) purely as a parity-testing baseline for `example`'s own test suite.

## A note on shipping a library alongside its own tests

`app` used to ship a `@SpringBootApplication` class in `src/main` (needed as a
`@SpringBootTest` anchor). When `example` was split out and its `ExampleApplication` declared a
wide `@ComponentScan(basePackages = "ro.cristivoicu.springbootrestless")` (needed to reach the
framework's packages, which aren't sub-packages of `example`'s own), that scan swept up `app`'s
leftover `@SpringBootApplication` class too, triggering a second, redundant auto-configuration
pass and duplicate bean definitions. Fixed by moving it to `app/src/test` — it's still on the
classpath for `app`'s own tests, but never shipped in the library jar a consumer's scan could
find. `app`'s `spring-boot-maven-plugin` was removed for the same reason: a library with no
main class has nothing to repackage, and repackaging (classes nested under `BOOT-INF/`) would
have broken consumption as a normal dependency anyway — see `app/pom.xml`.

## Project status

Working proof of the runtime-registration mechanism, the compile-time generator, and the
library/example split. 54 tests across both modules: `app`'s own suite (`app/src/test/.../fixtures/`,
`.../registry/`) proves the framework mechanism in isolation via `Gadget`/`Gizmo`/`Sprocket`;
`example`'s suite proves the same mechanism through realistic, business-named usage — full route
coverage, cross-resource isolation, duplicate-`basePath` detection, error-response parity against
the hand-written baseline, default-CUD end-to-end behavior, custom read actions, the
authorization guard's four scenarios, and the compile-time generator's convention-based +
override paths.
