# Changelog

All notable changes to this project are documented in this file. The format
is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
this project doesn't follow SemVer yet (still `0.0.1-SNAPSHOT`, pre-1.0) - see
[VERSIONING.md](VERSIONING.md) for what stability guarantees actually apply
before 1.0.

## [Unreleased]

### Added

- **Filter DSL.** `FilterOperator` + an extended `RestlessResourceHandler#getSpecification`
  reflection loop (`app`): a `SearchDto` field named `ageGte` now filters
  `age >= value` (and `Lte`/`Gt`/`Lt`/`Like`/`Ne`/`In` suffixes similarly),
  reflected over the same way a plain field already was — equality-only
  filtering was the gap, this is additive, not a rewrite. Wire format is
  camelCase (`?ageGte=30`), matching the Java field name exactly. Also new:
  `RestlessSpecifications`, a small fluent builder for hand-written
  `Specification` escape hatches, proven as a genuine drop-in by refactoring
  `GadgetRestlessResource`'s own hand-written filter and `byEmailDomain`
  custom read action onto it (all pre-existing Gadget tests pass unchanged).
  See `docs/design/filter-dsl.md`.

- **Write commands.** `WriteAction<E, Req, Resp>` / `getCustomWriteActions()`
  (`app`), mirroring the existing `ReadAction`/`getCustomReadActions()`
  mechanism: a named, intent-carrying mutation
  (`POST {basePath}/{id}/actions/{name}`) beyond the fixed create/update/
  patch/delete verbs, for a transition a full-replace `PUT` has no
  vocabulary to guard (illegal-state prevention, multi-step domain logic).
  Runs inside a real transaction (load + guard-check + `execute`), documented
  automatically by `RestlessOpenApiCustomizer`. New `AuthorizationGuard.Action.WRITE_ACTION`
  enum value (additive), and `CerbosActionNaming` now forwards a write action's
  own name to the policy (`cerbos` module) the same way it already did for
  named read actions/views. Hand-wired tier only for now — see
  `docs/design/write-commands.md` for the deferred `@RestlessEntity`/
  annotation-processor phase. Demonstrated end-to-end on `example`'s
  `Employee`: `promote` (illegal-transition prevention via a fixed `JobTitle`
  career ladder), `giveRaise` (a business-rule cap a bean-validation
  annotation can't express), `addCertification`/`recordAchievement`
  (append-only mutation of a collection `EmployeeUpdateModel` deliberately
  never exposes) — see `example/README.md`'s "Write commands" section.

- **Auto-configuration.** `RestlessAutoConfiguration` (`app`) and
  `CerbosAutoConfiguration` (`cerbos`), discovered via
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  A consumer no longer needs
  `@ComponentScan(basePackages = "ro.cristivoicu.springbootrestless")` - every
  framework infrastructure bean (`RestlessRegistrar`, `RestlessEmbedResolver`,
  `RestlessAuthorizationMetrics`, `RestlessExceptionHandler`,
  `RestlessOpenApiCustomizer`, the Cerbos client and health indicator) is a
  plain `@Bean` behind `@ConditionalOnMissingBean`/`@ConditionalOnClass` now,
  registered automatically the moment the jar is on the classpath.
- **`@RestlessResource(allowAll = ...)` / `@RestlessEntity(allowAll = ...)`.**
  `RestlessRegistrar` now refuses to register a resource that has no real
  `AuthorizationGuard` (still `AuthorizationGuard.allowAll()`) unless this is
  explicitly set to `true` - a startup-time `IllegalStateException` naming the
  resource, not a silently wide-open route. Fail-fast, not a runtime behavior
  change for any resource that already has a guard.
- **`restless.list.max-size`** (`RestlessProperties`, default 10,000) - a hard
  cap on `GET .../list`, previously unbounded. A response that hit the cap
  carries `X-Restless-List-Truncated: true`.
- **`Location` header + `201 Created`** on every single-item create (both the
  dynamic mechanism and the hand-subclassed `CreateController` tier), per RFC
  9110 §15.3.2 - previously `200 OK` with no `Location`.
- **`RestlessResourceHandler#inReadOnlyTransaction`** wraps every read path
  (`findOne`, `findList`, `findPage*`, `customRead`, `namedView`) in a
  `readOnly` transaction when a `PlatformTransactionManager` is configured -
  fixes a `LazyInitializationException` risk for consumers running with
  `spring.jpa.open-in-view=false` (the generally-recommended production
  setting) whose hand-written `Mapper`/`@RestlessEmbed` touches a lazy
  association. `example` now runs with OSIV off to prove this.
- `LICENSE` (Apache-2.0), `CONTRIBUTING.md`, `SECURITY.md`,
  `CODE_OF_CONDUCT.md`, this changelog, and a CI workflow
  (`.github/workflows/ci.yml`).

### Changed

- **Error responses are now RFC 9457 `application/problem+json`**
  (`org.springframework.http.ProblemDetail`), not a bespoke
  `{timestamp, status, error, message, path, details}` JSON shape. The old
  `ErrorResponse` record is removed; field-level validation messages now live
  under the `errors` extension member (present only on a validation failure),
  and `path` is now the standard `instance` member.
- **Bulk delete moved off `DELETE` with a request body.** `DELETE {basePath}`
  is now `POST {basePath}/bulk-delete` (same body shape, same
  `AuthorizationGuard.Action.DELETE_ALL`) - RFC 9110 gives a `DELETE` request
  body no defined semantics, and in practice proxies/CDNs/`fetch()` are known
  to drop it. Applies to both the dynamic mechanism and the hand-subclassed
  `DeleteController` tier.

### Fixed

- Nothing yet tracked separately from the above.

## Before this changelog existed

See the git history (`cf0b117` onward) for the Cerbos integration, exception
handling, OpenAPI generation, and versioning work that predates this file.
