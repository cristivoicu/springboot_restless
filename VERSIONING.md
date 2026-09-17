# Versioning and API stability

`spring-boot-restless` is pre-1.0 (every module currently ships as `0.0.1-SNAPSHOT`). This
document says, honestly, what that means for anyone depending on it today: what's stable enough
to build on, what's still free to change shape, and what to expect once 1.0 actually ships.

## Before 1.0

No compatibility guarantee exists yet across any release. A `0.0.1-SNAPSHOT` build today may not
be source- or binary-compatible with the next one. That said, churn is concentrated in specific
places (see below) — most of the framework's public surface has been stable for a while in
practice, just not yet under a stated promise.

## What's intended to be the stable surface once 1.0 ships

These are the extension points an entity author or a consuming application actually writes code
against, and are where compatibility will be prioritized first:

- `RestlessResourceHandler<E, K>` and its `get*DataSource()`/`getAuthorizationGuard()`/
  `getSpecification()`/`getCustomReadActions()`/`getEnabledOperations()` extension points.
- `AuthorizationGuard<E>` and its three hook points (`preCheck`/`scope`/`canAccess`).
- The `@Restless*` annotation set: `@RestlessEntity`, `@RestlessResource`, `@RestlessEmbed`,
  `@RestlessMapperExclude`, `@CerbosHiddenField`.
- The `*DataSource` base classes (`CreateDataSource`, `ReadDataSource`, `UpdateDataSource`,
  `DeleteDataSource`, `PatchDataSource`) and their `Default*DataSource` implementations.
- `Mapper<E, D>`, `SearchDto`/`AbstractSearchDto`, `SoftDeletable`, `AbstractAuditableEntity`.
- `CerbosAuthorizationGuard<E>`, `CerbosResourceAttributesMapper<E>`, `CerbosFieldMasker`.

## What's internal, and still expected to change shape

- `RestlessRegistrar`'s internals (route-registration mechanics, constructor parameter order).
- `ResourceMetadata`'s exact field list and constructor arity — it has already grown twice
  (per-projection response types, API version) and may again.
- `RestlessOpenApiCustomizer`'s internals (it's a best-effort documentation generator, not a
  contract any code should depend on beyond "produces a valid OpenAPI document").
- Anything under a `fixtures`/test-only package in any module.

## After 1.0

Standard semver: a breaking change to anything in the stable-surface list above ships only in a
new major version, with a changelog entry naming exactly what broke and why. A minor version may
add new optional attributes/methods (with defaults, so existing implementations keep compiling)
but never removes or repurposes an existing one. A patch version is bug fixes only.
