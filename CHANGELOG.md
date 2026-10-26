# Changelog

All notable changes to this project are documented here. The format is loosely based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project doesn't follow SemVer yet
(still `0.0.1`, pre-1.0) — see [Versioning and API Stability](README.md#versioning-and-api-stability)
in the README for what stability guarantees actually apply before 1.0.

## [0.0.1] - 2026-10-11

**Added**

- **Declarative ownership and server-set fields.** See
  [Ownership, denials and server-set fields](README.md#restlessownedby).
  - `@RestlessOwnedBy` (direct: `field = "ownerId"`; via a parent: `path`/`via`/`ownerField`)
    wires an `OwnershipAuthorizationGuard` built by the new `RestlessOwnershipGuards` bean: lists
    scoped in SQL (a subquery for the via shape), every single row checked, a create under
    someone else's parent rolled back, a nested parent checked once in `preCheck`. Fails closed
    with no principal. Paths are validated at compile time and again at startup.
  - `denyAs` on `@RestlessEntity`/`@RestlessResource` (`RestlessDenial.FORBIDDEN`/`NOT_FOUND`),
    global default `restless.authorization.deny-as`. `NOT_FOUND` makes a denied row answer
    exactly like a missing id, on every route including bulk.
  - `@RestlessFromPrincipal` - set from the caller on create, immutable afterwards; a
    create/update/patch model declaring the same field is a compile error. Create with no
    principal is a `401`.
  - `@RestlessImmutable(onUpdate = IGNORE | REJECT)`, global default `restless.immutable.on-update`.
  - `RestlessPrincipalIdResolver` bean (default: the authenticated name, anonymous counts as
    none), exposed as `RestlessContext.principalId()`.
- **Actions, files and lifecycle hooks on the generated tier.** See
  [Actions, files and lifecycle hooks](README.md#actions-views-files-and-hooks).
  - `@RestlessEntity` gains `readActions`, `collectionActions`, `writeActions`, `views` and
    `hooks` (bean classes), with `@RestlessActionName` naming each action/view - write commands
    and named views no longer need the hand-wired tier.
  - `CollectionAction<E>` - `GET {basePath}/actions/{name}`, handed the caller's visible rows as a
    `Specification`.
  - `RestlessBinary` - a non-JSON body (PDF, image, ZIP) from a view or action, streamed inside
    the route's transaction.
  - `multipartCreate` - `POST {basePath}` also accepts `multipart/form-data`, bound onto the
    create model (`MultipartFile` fields) and validated like JSON; otherwise `415`.
  - `RestlessLifecycleHook<E>` - `afterCreate`, `beforeUpdate`/`afterUpdate`,
    `beforeDelete`/`afterDelete`, inside the write's transaction, after authorization.
- **Bulk import, polymorphism, validation groups, error shape.** See
  [Bulk import, polymorphism, validation groups, error shape](README.md#bulk-operations).
  - `bulkMode = PARTIAL` (`RestlessBulkMode`) with an optional `RestlessDedupeKey` bean
    (`bulkDedupe`): per-row transactions, a `RestlessBulkResult` response, `?dedupe=false` to opt
    out. Authorization denials still abort the request.
  - `collectionGet = LIST` (`RestlessCollectionGet`), global default `restless.collection-get`.
  - `@RestlessSubtype` for polymorphic create through `DefaultCreateDataSource`.
  - `RestlessValidationGroups.OnCreate`/`OnUpdate`/`OnPatch`, applied on top of `Default`.
  - `RestlessErrorBodyFactory` - replace the `ProblemDetail` body on every generated route.
  - `@RestlessEntity(repository = ...)` - a repository outside the `{Entity}Repository` convention.
  - `RestlessOwnershipContract` in `spring-boot-restless-test` - verifies an owner/stranger
    ownership contract across every route in one call.
- **`example`**: `Notebook` (direct ownership, a collection action, a write action, a hook), `Note`
  (owned via its notebook, partial bulk with dedupe), `Attachment` (multipart upload, binary
  view, list mode) and `Shape` (polymorphism, validation groups, custom repository), each with
  end-to-end tests.

- **Nested (parent-scoped) resources and `RestlessContext`.** Two related additions, both driven
  by a real consumer integration (backend-nunti) that had to flatten `/api/events/{eventId}/guests`
  into `/api/guests?eventId=...` because the framework had no way to mount a resource under a
  parent's own path variable, and no way for a data source to see the request at all.
  - **`RestlessContext`** - a per-request object carrying the resolved path variables (plus a
    typed `pathVariable(name, Class)` accessor using the app's own `ConversionService`), the
    `HttpServletRequest`, the current `Authentication` (`Object`-typed - see its own javadoc for
    why - `app` has no mandatory Spring Security dependency, now an `optional=true` compile
    dependency purely so this can exist), and the `AuthorizationGuard.Action`/custom action name
    being checked. Every `*DataSource` verb gained a context-aware overload (`create(C, RestlessContext)`,
    `update(K, U, RestlessContext)`, ...) defaulting to the existing non-context one, so every
    hand-written data source keeps working unchanged - the handler always calls the context-aware
    overload. Also available anywhere via the static `RestlessContext.current()` (a nesting-safe
    thread-local, correctly restoring the outer context across a `@RestlessEmbed` cross-resource
    call), for a guard's own `preCheck` to read without a new parameter on `AuthorizationGuard`
    itself (which would have broken every existing implementation).
  - **`@RestlessParent`** (on `@RestlessEntity` and, identically shaped, `@RestlessResource`) -
    `basePath = "/events/{eventId}/guests", parent = @RestlessParent(variable = "eventId", field
    = "eventId")` mounts a resource under a parent's path variable. The parent field may be a flat
    scalar column (`eventId`, a `UUID`/`Long`) or one dotted `association.idProperty` segment
    (`event.id`, a real `@ManyToOne`) - the processor validates `field` at compile time for the
    generated tier (exists, isn't a type a path variable could never convert to); a runtime check
    in `RestlessResourceHandler#init` backstops both tiers (fail-fast if `variable` doesn't
    actually appear in `basePath`). Full semantics: every route (fixed/bulk/PATCH/named action)
    registers under the templated path, with duplicate-basePath detection treating `/a/{x}/b` and
    `/a/{y}/b` as the same route; create (single and bulk) writes the parent field from the path
    *after* the create model is applied, so a body-supplied value for the same field is always
    overwritten, never honored; every read auto-ANDs a `parentField = :pathValue` predicate onto
    its own filter, before the guard's `scope()`; a single-row route (`GET`/`PUT`/`PATCH`/
    `DELETE`/a named action) for a row under a *different* parent 404s, checked inside the same
    transaction, before `canAccess`; a bulk update/delete with even one foreign-parent target
    fails the whole batch with `404`, nothing written; update/patch can never move a row to a
    different parent ("protected like `@Id`" - forcibly reset after every write regardless of
    what the data source did); an unconvertible path value is a clean `400`; `RestlessOpenApiCustomizer`
    documents the parent path parameter (with the right schema type) on every route; `@RestlessEmbed`
    and soft delete keep working unchanged on a nested resource. See `example`'s `Task`
    (`/projects/{projectId}/tasks`, the dotted-association form) and its `TaskAuthorizationGuardBean`
    (a guard cheaply authorizing the parent once per request via `RestlessContext` in `preCheck`,
    Feature B item 8) for an end-to-end proof, and the "Nested (parent-scoped) resources" section
    below for the full guide.

- **Conditional requests (Phase 2 item 9).** `findOne`/`namedView`/`create`/`update`/`patch` now
  emit a strong `ETag` from `@Version` on single-resource responses. `GET` with `If-None-Match`
  short-circuits to `304` (weak comparison, per RFC 9110). `If-Match` now supports `*` and a
  comma-separated candidate list, under *strong* comparison - a weak (`W/"..."`) candidate is
  excluded entirely rather than stripped-and-compared, since a weak validator can never satisfy
  strong comparison; **this is a behavior tightening**, not purely additive - a client that was
  relying on a weak `If-Match` validator being accepted (today's lenient, technically
  RFC-incorrect behavior) will now get `412` instead. `If-Match` is also now optionally honored
  on named write actions (previously not checked there at all). `readVersion` now walks
  superclasses - a `@Version` on a shared `@MappedSuperclass` was previously invisible.

- **Tooling (Phase 3 item 17).**
  - **JaCoCo**: `app`/`processor`/`cerbos` each produce a per-module coverage report
    (`target/site/jacoco/index.html`) on `mvn verify`.
  - **japicmp**: wired into `app`'s pom against the stable-surface packages (see "Versioning and
    API Stability" above), but there is no previously published release of this artifact yet to
    diff against - `ignoreMissingOldVersion` makes that a correctly-wired no-op today rather than
    a build failure; it starts actually comparing the moment a real first version is published.
  - **JSpecify**: every main package in `app`/`processor`/`cerbos` now has a `package-info.java`
    with `@NullMarked` - documentation of intent only, no null-checking build step wired in.
  - **PostgreSQL Testcontainers for `example`**: `PostgresBackedTest` + `DepartmentPostgresCudTest`
    (tagged `"postgres"`, excluded from the default H2-backed run) exercise the full CUD cycle
    against a real Postgres container instead of H2's emulation of one - catches the class of
    `LIKE`-wildcard/collation/null-ordering bug H2 papers over. CI runs it as its own job
    (`-Dgroups=postgres`), separate from the fast default run.

- **Java 17 baseline for the shipped library (Phase 3 item 16).** `app`/`processor`/`cerbos`/
  `spring-boot-restless-test` now build down to Java 17 (`example`, never shipped, stays on 25).
  One real Java-21-only construct turned up along the way and was rewritten: `CerbosAttributeValues`
  used a `switch` with type patterns (JEP 441, finalized in 21) - replaced with a plain
  `instanceof`-pattern chain, identical behavior. CI now builds a `[17, 21, 25]` matrix; the 17/21
  legs build only the library modules (`example`'s own sources target `--release 25`, which a
  JDK 17/21 `javac` can't do regardless of the library's own compatibility).

- **Generated mapper codegen (Phase 3 item 15).** The default generated `{Entity}Mapper` (when
  no hand-written one exists) no longer uses reflective `BeanUtils.copyProperties` - it now emits
  one explicit `dto.setX(source.getX())` call per matched, non-`@RestlessMapperExclude` field
  pair, compile-time type-checked, a step toward GraalVM native-image support. Also declared
  `isolating` in `META-INF/gradle/incremental.annotation.processors`, so a Gradle incremental
  build only reprocesses the `@RestlessEntity` classes whose own source actually changed.

- **Soft-delete write/read consistency (Phase 2 item 13).** A write (`update`/`patch`/
  `deleteById`/a named write action) on an already-soft-deleted row now 404s unconditionally -
  previously the write path had no soft-delete awareness at all and happily proceeded as if the
  row were any other. New `restless.soft-delete.include-in-single-read` property (default `true`
  - today's pre-item-13 behavior: `findOne`/a named view still returns a soft-deleted row);
  set to `false` to 404 it there too, same as a row that never existed. Threaded through the new
  `RestlessInitContext.includeSoftDeletedInSingleRead` field.

- **Fail-closed field masking (Phase 2 item 12).** New `AuthorizationGuard.postProcessResponse(action,
  customActionName, request, entity, dto)` default hook (returns `dto` unchanged) -
  `RestlessResourceHandler` now calls it right after `Mapper.map(...)` on every single-entity
  response (`create`/`findOne`/`namedView`/`update`/`patch`). `CerbosAuthorizationGuard` overrides
  it to automatically run `CerbosFieldMasker` against any `@CerbosHiddenField`-annotated DTO field
  - a hand-written `Mapper` no longer has to remember to call the masker itself (one that already
  does keeps working unaffected; masking is idempotent). Skips the Cerbos `check()` RPC entirely
  when the DTO type carries no `@CerbosHiddenField` field at all. If the PDP can't be reached to
  say which fields to hide, every `@CerbosHiddenField` field is masked rather than none - fail
  closed, consistent with this guard's other hooks.

- **Cerbos query plan translator hardening (Phase 2 item 11).** `eq`/`ne` against a `null`
  literal now translate to `IS NULL`/`IS NOT NULL` instead of `cb.equal(path, null)` (which
  Hibernate does not treat as a null check, so a condition like `request.resource.attr.ownerId ==
  null` previously silently matched nothing at all). `coerce()` now also widens a `String` literal
  to an `enum` constant, a `UUID`, or a `java.time` value (`Instant`/`LocalDate`/
  `LocalDateTime`/`OffsetDateTime`) when the target attribute's Java type calls for one - Cerbos's
  own `Value` has no representation for any of those, so a policy condition comparing one of these
  columns always carries it as a plain string. `CerbosAuthorizationGuard.scope()` now fails closed
  (denies) instead of surfacing an unhandled `500` when the translator hits a query-plan shape it
  doesn't support. `principalAttributesExtender` - documented as potentially a real database
  lookup - is now resolved at most once per request (cached on the `HttpServletRequest` itself)
  instead of once per `preCheck`/`canAccess`/`scope` call.

- **Bulk performance (Phase 2 item 10).** `updateBulk`/`deleteAll` now resolve their whole
  pre-image/pre-check batch with one `findAllById` + one `canAccessAll` call, instead of looping
  `findOne`/`canAccess` per id - one query and one authorization decision per bulk request
  instead of N of each. `ReadDataSource` gained a concrete `findAllById(Collection<K>)` (default:
  loops `findOne`, so any existing subclass keeps working unchanged); `DefaultReadDataSource`
  overrides it with a single `specificationRepository.findAllById(...)` call.
  `AuthorizationGuard` gained a `default canAccessAll(Action, HttpServletRequest, List<E>)`
  (default: loops `canAccess`, same end result as before - override it for a guard that can check
  a whole batch in one round trip, e.g. a single batched policy-engine call instead of N).
  `DefaultDeleteDataSource`/`DefaultSoftDeleteDataSource` also gained a 3-arg constructor
  (`repository, idType, conversionService`) alongside the existing 2-arg one - bulk-delete id
  conversion previously always went through a standalone `DefaultConversionService`, silently
  bypassing any custom `Converter` bean a consumer had registered on the app's own
  `ConversionService`; processor-generated resources now pass the real one through.

- **Internals cleanup (Phase 2 item 14).** `getAuthorizationGuard()` is now resolved once, in
  `init()`, and cached - every internal check (`checkPreCheck`/`checkCanAccess`/`scope()`/...)
  reads that cached instance instead of calling the (overridable, possibly expensive-to-construct)
  accessor again on every single request. `init()`'s six telescoping overloads are replaced by
  one `init(RestlessInitContext)` - a plain record naming every collaborator, so a future new one
  doesn't need a seventh overload. Transaction demarcation
  (`inTransaction`/`inReadOnlyTransaction`) and `If-Match`/`@Version` precondition handling moved
  into new package-private `TransactionSupport`/`PreconditionSupport` classes - no change to
  `RestlessResourceHandler`'s own protected/public surface, both were already private there.

- **Filter DSL hardening.** Every `SearchDto` field/operator binding is now precomputed and
  validated once at startup (not per request, and not via per-request `getDeclaredFields`/
  `setAccessible`) - an equality field naming no real entity property, an operator the resolved
  property's type doesn't support, `In` on a non-`Collection` field, and a primitive filter field
  now all fail fast at startup instead of being silently ignored or thrown from inside a running
  query. A suffixed field whose *full* name is itself a real entity property (e.g. `checkIn`) is
  now treated as plain equality on that property rather than silently dropped as an unresolvable
  `In` operator on a nonexistent `check` base property. `Like` now escapes a literal `%`/`_`/`\`
  in the search term (previously a literal `%` in a search term was interpreted as a SQL
  wildcard). New `ILike` (case-insensitive contains) and `StartsWith` (index-friendly prefix
  match, no leading wildcard) operators.

- **`@RestlessEmbed` now respects the target's `getEnabledOperations()`, excludes soft-deleted
  rows, and applies `scope()` on a `many = false` embed too.** Previously `?expand=name` could
  reach `findEmbeddedList`/`findEmbeddedOne` directly, bypassing a target resource's own
  `READ_LIST`/`READ_ONE` opt-out, its soft-delete exclusion, and (for `many = false`) its
  row-level `scope()` entirely - none of which the embedded field's own route (there isn't one)
  would have let through. `findEmbeddedOne` also now queries with a limit of 2 and throws if the
  join matches more than one row, instead of silently taking the first - more than one match
  means `sourceField`/`targetField` don't actually form the natural key `@RestlessEmbed`
  assumes, which is a configuration error worth surfacing, not papering over.

- **Atomic write pipeline.** `create`/`update`/`patch`/`deleteById` (and `createBulk`/
  `updateBulk`) now run load, 404-if-missing, `canAccess`, `If-Match`, the data-source write,
  a flush, and `Mapper.map` inside one transaction, in that order - previously the guard/
  `If-Match` check loaded the entity separately, outside any transaction, and the data source
  reloaded and wrote independently. Two consequences, now closed: a concurrent write landing
  between the check and this request's own write now surfaces as `409`/`412`, never a silent
  overwrite (see the new `ConcurrentUpdateRaceTest`); and a write-response `Mapper` touching a
  lazy association now works under `spring.jpa.open-in-view=false` (see
  `CreateWithLazyAssociationOsivOffTest`) - previously nothing in the write path ran inside a
  transaction/session at all. `DataSource` gained a `flush()` method (public, concrete) so
  `RestlessResourceHandler` can force a pending write to actually hit the database before
  mapping the result.
- **Row-level authorization on create, extended to writes generally.** `create`
  (and `createBulk`) now calls `canAccess(CREATE, request, newEntity)` *after* the data source
  saves it, inside the same transaction - a denial rolls the insert back. Previously create
  never called `canAccess` at all; a resource's own guard had no say over what a client could
  create. New `AuthorizationGuard#canAccessAfterWrite(Action, String, HttpServletRequest, E
  after)` default method, additive, defaulting to `canAccess(...)` - `update`/`patch` (and
  `updateBulk`) now call it *after* the write, on the now-mutated entity, in addition to the
  existing pre-image check before it. **Default-on behavior change worth knowing about**: a
  guard whose `canAccess` inspects mutable state that a legitimate update is expected to change
  (e.g. `status == DRAFT` as part of an allowed draft→published transition) will now also run
  that same check against the *new* state, and could unexpectedly deny a transition it used to
  allow. Whether `canAccessAfterWrite` should default to `canAccess` at all, versus defaulting
  to "always allow" (strictly opt-in), is flagged as a decision worth making before 1.0.

- **Mass-assignment protection.** `Default{Create,Update,Patch}DataSource`'s `BeanUtils.copyProperties`
  now ignores whichever of the entity's `@Id`/`@Version`/`SoftDeletable`-`deleted`/audit-timestamp
  property names it finds (`ProtectedEntityFields`) - previously a `CreateModel` carrying a
  populated `id` could make `save()` merge onto an existing row instead of inserting a new one,
  bypassing that row's `UPDATE` guard entirely. `RestlessEntityProcessor` now also raises a compile
  error when a Create/Update/PatchModel declares one of those protected field names, or when a
  PatchModel field is primitive (it can never be `null`, so `PATCH` would always overwrite it).
- **`restless.page.max-size`** (default `2000`, matching Spring Data's own
  `spring.data.web.pageable.max-page-size` default) - a client-supplied `size` over this on
  `GET {basePath}`/`.../overview`/`.../select/async`/a named read action is now rejected `400`,
  previously unbounded.
- **`restless.bulk.max-size`** (default `1000`) - `createBulk`/`updateBulk`/`deleteAll` now reject
  `400` for a request carrying more items than this, checked before any item is processed.
- **Bound `SearchDto`s are now validated.** `bindSearchDto` runs the bound instance through the
  same `Validator` request bodies already go through - a `@Max`/`@Min`/... on a `SearchDto` field
  was silently never enforced before this.
- **`findEmbeddedList` is now capped** by `restless.list.max-size`, the same cap every other
  unbounded read already had - an `@RestlessEmbed(many = true)` field was the one unbounded read
  that cap didn't reach yet.
- **Default sort resolves the entity's real `@Id` property.** Previously `AbstractSearchDto`
  always defaulted an unsorted request to a hardcoded `"id"` sort - a 400 on every single
  unsorted request to any entity whose `@Id` isn't literally named `id` (a natural key, say).
  An explicit client sort that doesn't already end in the id property now also gets it appended
  as a tie-breaker, a prerequisite for stable pagination. Sortable properties are now also
  restricted to plain scalar columns - associations, collections, static/transient fields, and
  any property whose response DTO counterpart is `@CerbosHiddenField` are excluded (previously
  some of these produced a confusing 500 from Hibernate instead of a clean 400; sorting by a
  masked field would have leaked its order).
- **Repo documentation consolidated.** Every standalone governance/meta doc (`ROADMAP.md`,
  `VERSIONING.md`, `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`, `SECURITY.md`, this changelog) and the
  three `docs/design/*.md` design-rationale docs are now sections of this README instead of
  separate files — fewer files/folders to navigate for a first-time visitor. `docs/DEEP_DIVE.md`
  (the full tutorial-depth walkthrough) is unaffected.
- **Claude Code Agent Skill.** `.claude/skills/spring-boot-restless/` teaches Claude Code this
  framework's conventions - the three-tier entity decision framework, authorization/Cerbos wiring,
  write commands, bulk operations, and the filter DSL - split into a concise `SKILL.md` plus
  on-demand `reference/*.md` files. Loads automatically while working in this repo; copy the
  directory into a consumer project's own `.claude/skills/` for the same assistance there.
- **Repo made publish-ready.** `README.md` restructured into a concise, template-shaped reference
  (badges, requirements, installation, configuration, quick start, feature guide); the previous
  full tutorial-depth content (every mechanism, sequence diagrams, the three-tier decision
  framework, worked examples) moved to `docs/DEEP_DIVE.md` instead of competing with it.
  GitHub issue templates (`bug_report.md`/`feature_request.md`) and a `PULL_REQUEST_TEMPLATE.md`
  added under `.github/`. Root `pom.xml` now declares a `<licenses>` block (Apache-2.0).
- **Filter DSL.** `FilterOperator` + an extended `RestlessResourceHandler#getSpecification`
  reflection loop (`app`): a `SearchDto` field named `ageGte` now filters `age >= value` (and
  `Lte`/`Gt`/`Lt`/`Like`/`Ne`/`In` suffixes similarly), reflected over the same way a plain field
  already was — equality-only filtering was the gap, this is additive, not a rewrite. Wire format
  is camelCase (`?ageGte=30`), matching the Java field name exactly. Also new:
  `RestlessSpecifications`, a small fluent builder for hand-written `Specification` escape
  hatches, proven as a genuine drop-in by refactoring `GadgetRestlessResource`'s own hand-written
  filter and `byEmailDomain` custom read action onto it (all pre-existing Gadget tests pass
  unchanged). See [Design Rationale](README.md#design-rationale).
- **Write commands.** `WriteAction<E, Req, Resp>` / `getCustomWriteActions()` (`app`), mirroring
  the existing `ReadAction`/`getCustomReadActions()` mechanism: a named, intent-carrying mutation
  (`POST {basePath}/{id}/actions/{name}`) beyond the fixed create/update/patch/delete verbs, for a
  transition a full-replace `PUT` has no vocabulary to guard (illegal-state prevention,
  multi-step domain logic). Runs inside a real transaction (load + guard-check + `execute`),
  documented automatically by `RestlessOpenApiCustomizer`. New
  `AuthorizationGuard.Action.WRITE_ACTION` enum value (additive), and `CerbosActionNaming` now
  forwards a write action's own name to the policy (`cerbos` module) the same way it already did
  for named read actions/views. Hand-wired tier only for now — see [Design Rationale](README.md#design-rationale)
  for the deferred `@RestlessEntity`/annotation-processor phase. Demonstrated end-to-end on
  `example`'s `Employee`: `promote` (illegal-transition prevention via a fixed `JobTitle` career
  ladder), `giveRaise` (a business-rule cap a bean-validation annotation can't express),
  `addCertification`/`recordAchievement` (append-only mutation of a collection
  `EmployeeUpdateModel` deliberately never exposes) — see `example/README.md`'s "Write commands"
  section.
- **Auto-configuration.** `RestlessAutoConfiguration` (`app`) and `CerbosAutoConfiguration`
  (`cerbos`), discovered via
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. A consumer
  no longer needs `@ComponentScan(basePackages = "ro.cristivoicu.springbootrestless")` - every
  framework infrastructure bean (`RestlessRegistrar`, `RestlessEmbedResolver`,
  `RestlessAuthorizationMetrics`, `RestlessExceptionHandler`, `RestlessOpenApiCustomizer`, the
  Cerbos client and health indicator) is a plain `@Bean` behind
  `@ConditionalOnMissingBean`/`@ConditionalOnClass` now, registered automatically the moment the
  jar is on the classpath.
- **`@RestlessResource(allowAll = ...)` / `@RestlessEntity(allowAll = ...)`.** `RestlessRegistrar`
  now refuses to register a resource that has no real `AuthorizationGuard` (still
  `AuthorizationGuard.allowAll()`) unless this is explicitly set to `true` - a startup-time
  `IllegalStateException` naming the resource, not a silently wide-open route. Fail-fast, not a
  runtime behavior change for any resource that already has a guard.
- **`restless.list.max-size`** (`RestlessProperties`, default 10,000) - a hard cap on
  `GET .../list`, previously unbounded. A response that hit the cap carries
  `X-Restless-List-Truncated: true`.
- **`Location` header + `201 Created`** on every single-item create (both the dynamic mechanism
  and the hand-subclassed `CreateController` tier), per RFC 9110 §15.3.2 - previously `200 OK`
  with no `Location`.
- **`RestlessResourceHandler#inReadOnlyTransaction`** wraps every read path (`findOne`,
  `findList`, `findPage*`, `customRead`, `namedView`) in a `readOnly` transaction when a
  `PlatformTransactionManager` is configured - fixes a `LazyInitializationException` risk for
  consumers running with `spring.jpa.open-in-view=false` (the generally-recommended production
  setting) whose hand-written `Mapper`/`@RestlessEmbed` touches a lazy association. `example` now
  runs with OSIV off to prove this.
- `LICENSE` (Apache-2.0), this changelog, and a CI workflow (`.github/workflows/ci.yml`).

**Changed**

- **`RestlessExceptionHandler`'s handler methods now return `ResponseEntity<Object>`** (was
  `ResponseEntity<ProblemDetail>`), so a `RestlessErrorBodyFactory` can supply a different body
  type. Source-incompatible for a subclass overriding them or code reading `getBody()` as a
  `ProblemDetail` without a cast; the default body is still a `ProblemDetail`.
- **`DefaultCreateDataSource` writes server-managed fields before the `INSERT`.** A nested
  resource's parent field and `@RestlessFromPrincipal` fields are now set on the entity before
  `save`, not only re-applied afterwards - so they can be `NOT NULL` columns. (`example`'s
  `Task.project` is now mandatory to prove it.) The handler still re-applies them after any data
  source, as before.
- **Error responses are now RFC 9457 `application/problem+json`**
  (`org.springframework.http.ProblemDetail`), not a bespoke
  `{timestamp, status, error, message, path, details}` JSON shape. The old `ErrorResponse` record
  is removed; field-level validation messages now live under the `errors` extension member
  (present only on a validation failure), and `path` is now the standard `instance` member.
- **Bulk delete moved off `DELETE` with a request body.** `DELETE {basePath}` is now
  `POST {basePath}/bulk-delete` (same body shape, same `AuthorizationGuard.Action.DELETE_ALL`) -
  RFC 9110 gives a `DELETE` request body no defined semantics, and in practice proxies/CDNs/
  `fetch()` are known to drop it. Applies to both the dynamic mechanism and the hand-subclassed
  `DeleteController` tier.
- **`RestlessExceptionHandler` is now scoped** (`@RestControllerAdvice(assignableTypes =
  RestlessErrorScope.class)`) to restless handlers and the hand-subclassed `*Controller` tier -
  previously an unscoped advice, so its `Exception` catch-all changed error handling for a
  consumer's own, unrelated controllers too (e.g. turning a Spring Security
  `AuthorizationDeniedException` into a generic 500). `RestlessResourceHandler` and the four
  `*Controller` base classes now implement the new `RestlessErrorScope` marker interface.
- **Every `ProblemDetail` now carries a stable `type` URI**, one per error kind (e.g.
  `.../problems/validation-failed`, `.../problems/not-found`) - previously the RFC 9457 default
  of `about:blank` for every response, which can't distinguish one error kind from another.
- **`errors` entries are now objects (`{field, message, code}`)**, not a single
  `"field: message"` string - a client parsing them programmatically had no reliable way to
  separate the field name from the message, or key off the violated constraint at all.
- **`DataIntegrityViolationException` now maps to `409`** with a generic detail - previously
  fell through to the generic `500` handler, which also meant the underlying SQL exception's own
  message (routinely embedding table/column/constraint names, even literal values) could leak
  into the response body.

**Fixed**

- **A `CreateDataSource` overriding `createAll(List)` was silently bypassed on bulk create.** The
  handler always calls the context-aware `createAll(List, RestlessContext)`, whose default looped
  the single-item `create` - so a batched, validate-everything-first override never ran. The
  request's own transaction still rolled back on a bad row, but inside an enclosing transaction
  (a `@Transactional` test, a calling service) the earlier good rows stayed visible. The default now delegates to an overridden `createAll(List)`. Caught by
  `example`'s `ProjectAssignmentTest`.
- **`@Id` declared on a shared `@MappedSuperclass` (e.g. a project-wide `BaseEntity`) failed
  compilation** - `RestlessEntityProcessor` only ever looked at the annotated entity's own
  declared fields, so an entity whose `@Id` actually lived on its superclass hit "has no field
  annotated `@jakarta.persistence.Id`" even though one genuinely existed. `resolveIdType` now
  walks superclasses the same way `declaredFieldsOf`/`reservedEntityFieldNames` already did,
  resolving a generic superclass's own type argument (`BaseEntity<K>`) via `Types.asMemberOf`
  rather than returning the erased type variable name. See `example`'s own `Skill`/`BaseEntity`
  (`entity/shared`) for the end-to-end proof, and `RestlessEntityProcessorInheritedIdTest`
  (`processor` module) for the isolated compile-time one.
- **A single `?sort=field,direction` (one occurrence, not the repeated-param form) was silently
  broken** - `?sort=date,asc` returned `400 "Unknown sort property 'asc'"`. `ServletRequestDataBinder`
  hands a single-occurrence query parameter to the conversion service as a plain `String`, not a
  one-element array; converting that onto `AbstractSearchDto#sort`'s `List<String>` then goes
  through `DefaultConversionService`'s `StringToCollectionConverter`, which splits on *every*
  comma in the whole string - turning one `"date,asc"` entry into two broken ones, `"date"` and
  `"asc"`, each parsed as its own property with no direction. A repeated `sort` param
  (`?sort=a,asc&sort=b,desc`) never hit this, since Spring binds a multi-value parameter as a real
  array with no comma-splitting involved - which is why only the single-value form was broken.
  `RestlessResourceHandler` now reads `sort`'s raw request-parameter values directly instead of
  leaving it to the generic binder, bypassing the faulty conversion entirely. Also added: a
  leading `-` as shorthand for descending (`?sort=-lastName`, same convention GitHub's and many
  other REST APIs' own sort parameters use).
- `SECURITY.md`/[Security Policy](README.md#security-policy) pointed at a `<developers>` section in the
  root `pom.xml` that never existed - dead end for anyone trying to report a vulnerability
  privately. Now relies solely on GitHub's private vulnerability reporting (Security tab).
- An in-flight `@RequiredArgsConstructor` change to `RestlessRegistrar` would have dropped its
  `ObjectProvider<RestlessAuthorizationMetrics>` fallback (breaking resources with no
  Actuator/Micrometer on the classpath) and its null-default for `RestlessProperties` - caught
  before it was ever committed; `RestlessRegistrar` keeps its explicit constructor.

**Before this changelog existed:** see the git history (`cf0b117` onward) for the Cerbos
integration, exception handling, OpenAPI generation, and versioning work that predates this file.

