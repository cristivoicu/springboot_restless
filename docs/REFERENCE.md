# spring-boot-restless — API reference

Every public component the library ships, grouped by what you use it for. For each one: which
artifact it lives in, what it does, and how to use it. The [README](../README.md) is the short
introduction. [`DEEP_DIVE.md`](DEEP_DIVE.md) has the long tutorials with diagrams. This document
is the place to look a component up.

All types live under the `ro.cristivoicu.springbootrestless` root package. Package names below are
relative to it, so `annotation.RestlessEntity` means
`ro.cristivoicu.springbootrestless.annotation.RestlessEntity`.

## Contents

- [Artifacts at a glance](#artifacts-at-a-glance)
- [HTTP routes a resource exposes](#http-routes-a-resource-exposes)
- [1. Declaring a resource (compile time)](#1-declaring-a-resource-compile-time)
  - [`@RestlessEntity`](#restlessentity)
  - [`@RestlessReadEntity`](#restlessreadentity)
  - [`@RestlessOwnedBy`](#restlessownedby)
  - [`@RestlessParent`](#restlessparent)
  - [`RestlessOperation`](#restlessoperation)
  - [`@RestlessMapperExclude`](#restlessmapperexclude)
  - [`RestlessEntityProcessor` and what it generates](#restlessentityprocessor-and-what-it-generates)
- [2. Declaring a resource (runtime / manual tier)](#2-declaring-a-resource-runtime--manual-tier)
  - [`@RestlessResource`](#restlessresource)
  - [`RestlessReadResourceHandler<E, K>`](#restlessreadresourcehandlere-k)
  - [`RestlessResourceHandler<E, K>`](#restlessresourcehandlere-k)
  - [`RestlessRoute`](#restlessroute)
  - [`ResourceMetadata` and `RestlessInitContext`](#resourcemetadata-and-restlessinitcontext)
- [3. Behaviour enums](#3-behaviour-enums)
  - [`RestlessDenial`](#restlessdenial)
  - [`RestlessBulkMode`](#restlessbulkmode)
  - [`RestlessCollectionGet`](#restlesscollectionget)
- [4. Field annotations](#4-field-annotations)
  - [`@RestlessFromPrincipal`](#restlessfromprincipal)
  - [`@RestlessImmutable`](#restlessimmutable)
  - [`@RestlessEmbed`](#restlessembed)
- [5. Models and DTO contracts](#5-models-and-dto-contracts)
- [6. Persistence: repositories and data sources](#6-persistence-repositories-and-data-sources)
- [7. Mapping](#7-mapping)
- [8. Authorization](#8-authorization)
- [9. Per-request context](#9-per-request-context)
- [10. Actions, views, files and hooks](#10-actions-views-files-and-hooks)
- [11. Bulk writes](#11-bulk-writes)
- [12. Filtering](#12-filtering)
- [13. Embedding](#13-embedding)
- [14. Errors](#14-errors)
- [15. Infrastructure and configuration](#15-infrastructure-and-configuration)
- [16. Hand-subclassed controllers (legacy tier)](#16-hand-subclassed-controllers-legacy-tier)
- [17. Cerbos module](#17-cerbos-module)
- [18. Test module](#18-test-module)
- [Index (A–Z)](#index-az)

---

## Artifacts at a glance

| Artifact | Scope | What it contains |
|---|---|---|
| `spring-boot-restless` | `compile` | No code. Depends on `-read` and `-write`. The default choice. |
| `spring-boot-restless-read` | `compile` | Read handler, GET routes, guards, filter DSL, embedding, errors, OpenAPI, auto-configuration, runtime annotations. |
| `spring-boot-restless-write` | `compile` | Write handler, create/update/patch/delete data sources, write models, write actions, hooks, bulk results, `@RestlessSubtype`. Depends on `-read`. |
| `spring-boot-restless-processor` | `provided` / `annotationProcessor` | Compile-time annotations (`@RestlessEntity`, `@RestlessReadEntity`, `@RestlessOwnedBy`, ...) and the annotation processor. Never on the runtime classpath. |
| `spring-boot-restless-cerbos` | `compile`, optional | Cerbos-backed guard, field masking, health indicator. Depends on `-read` only. |
| `spring-boot-restless-test` | `test`, optional | MockMvc matchers and the ownership contract check. |
| `spring-boot-restless-dependencies` | `import` (BOM) | Pins the version of every artifact above. |

A few types exist twice, once in `-processor` (`SOURCE` retention, read by the annotation
processor) and once in `-read` (`RUNTIME` retention, read at startup): `RestlessParent`,
`RestlessDenial`, `RestlessBulkMode` and `RestlessCollectionGet`. They have the same package, name
and meaning. The processor copies the compile-time values onto the generated resource's runtime
`@RestlessResource`. You import the same name either way.

## HTTP routes a resource exposes

`{basePath}` is the resource's base path, such as `/tasks` or `/events/{eventId}/guests`.

| Verb | Path | Handler method | Guard action | Present when |
|---|---|---|---|---|
| `POST` | `{basePath}` | `create` | `CREATE` | write resource, `CREATE` enabled |
| `POST` | `{basePath}/bulk` | `createBulk` | `CREATE` | same as `create` |
| `GET` | `{basePath}/{id}` | `findOne` | `READ_ONE` | `READ_ONE` enabled |
| `GET` | `{basePath}/list` | `findList` | `READ_LIST` | `READ_LIST` enabled |
| `GET` | `{basePath}` | `findPage` (or `findList` with `collectionGet = LIST`) | `READ_PAGE` | `READ_PAGE` enabled |
| `GET` | `{basePath}/page` | `findPage` | `READ_PAGE` | only with `collectionGet = LIST` |
| `GET` | `{basePath}/overview` | `findPageOverview` | `READ_PAGE_OVERVIEW` | enabled |
| `GET` | `{basePath}/select/async` | `findPageSelect` | `READ_PAGE_SELECT` | enabled |
| `PUT` | `{basePath}/{id}` | `update` | `UPDATE` | write resource, `UPDATE` enabled |
| `PUT` | `{basePath}/bulk` | `updateBulk` | `UPDATE` | same as `update` |
| `PATCH` | `{basePath}/{id}` | `patch` | `PATCH` | a patch data source is configured |
| `DELETE` | `{basePath}/{id}` | `deleteById` | `DELETE_ONE` | write resource, `DELETE_ONE` enabled |
| `POST` | `{basePath}/bulk-delete` | `deleteAll` | `DELETE_ALL` | write resource, `DELETE_ALL` enabled |
| `GET` | `{basePath}/actions/{name}` | `customRead` | `CUSTOM_READ` + name | one per `ReadAction` |
| `GET` | `{basePath}/actions/{name}` | `collectionAction` | `CUSTOM_READ` + name | one per `CollectionAction` |
| `GET` | `{basePath}/{id}/{name}` | `namedView` | `NAMED_VIEW` + name | one per named view |
| `POST` | `{basePath}/{id}/actions/{name}` | `writeAction` | `WRITE_ACTION` + name | one per `WriteAction` |

Request and response conventions shared by every route:

- **Paging and sorting:** `?page=0&size=20&sort=name,asc&sort=-id`. `size` is capped by
  `restless.page.max-size`. Larger values get a `400`.
- **Filtering:** every non-null field of the search DTO, see [Filtering](#12-filtering).
- **Embedding:** `?expand=a,b` on `findOne` and named views, see [Embedding](#13-embedding).
- **Page envelope:** `{"totalPages", "totalElements", "pageSize", "body": [...]}`
  ([`PageableResponse`](#pageableresponseb)).
- **Create:** `201 Created` with a `Location` header.
- **Delete:** `204 No Content`.
- **Truncated list:** a `/list` response that hit `restless.list.max-size` carries
  `X-Restless-List-Truncated: true`.
- **Conditional requests** (entity has `@Version`): single-entity responses carry a strong `ETag`.
  `If-None-Match` on `GET` can return `304`. `If-Match` on `PUT`/`PATCH`/`DELETE`/write actions
  can return `412`.
- **Errors:** RFC 9457 `application/problem+json`, see [Errors](#14-errors).

---

## 1. Declaring a resource (compile time)

These annotations live in `spring-boot-restless-processor` and have `SOURCE` retention. Add the
processor as a `provided` / `annotationProcessor` dependency to use them.

### `@RestlessEntity`

`annotation.RestlessEntity` · on a JPA `@Entity` class · artifact `-processor`

Generates a complete read-write REST resource for the entity. You write the entity and its DTOs.
The processor writes the repository (if missing), the mapper (if missing) and the
`{Entity}RestlessResource` class.

```java
@Entity
@RestlessEntity(basePath = "/tasks", authorizationGuard = TaskGuard.class)
public class Task { @Id @GeneratedValue private Long id; private String title; }
```

The processor finds the DTOs by naming convention, in the entity's own package:

| Role | Convention | Must implement / extend |
|---|---|---|
| Create body | `{Entity}CreateModel` | [`CreateModel`](#createmodel-updatemodel-patchmodel) |
| Update body | `{Entity}UpdateModel` | [`UpdateModel`](#createmodel-updatemodel-patchmodel) |
| Search / filter | `{Entity}SearchDto` | [`SearchDto`](#searchdto-and-abstractsearchdto) (usually via `AbstractSearchDto`) |
| Response | `{Entity}Dto` | any class with a no-arg constructor (`EntityDto` by convention) |
| Mapper (optional) | `{Entity}Mapper` | [`Mapper<Entity, Dto>`](#mappere-d), a Spring bean. Generated if missing. |
| Repository (optional) | `{Entity}Repository` | [`SpecificationRepository<Entity, Id>`](#specificationrepositorye-k). Generated if missing. |

Attributes:

| Attribute | Default | Meaning |
|---|---|---|
| `basePath` | required | Mount path, e.g. `"/tasks"` or `"/events/{eventId}/guests"`. |
| `createModel`, `updateModel`, `searchDto`, `dto` | convention | Point at a DTO that doesn't follow the naming convention. |
| `mapper` | convention, else generated | A hand-written `Mapper<Entity, ?>` bean. |
| `repository` | convention, else generated | A repository in another package. Must extend `SpecificationRepository<Entity, Id>`. |
| `createDataSource`, `readDataSource`, `updateDataSource`, `deleteDataSource` | `Default*DataSource` | A hand-written data source bean for that one verb. See [data sources](#6-persistence-repositories-and-data-sources). |
| `patchDataSource` | none (no `PATCH` route) | A `PatchDataSource` bean. Setting it adds `PATCH {basePath}/{id}`. Use a small named subclass of `DefaultPatchDataSource`, since a `Class<?>` attribute can't name a generic type. |
| `authorizationGuard` | none | An `AuthorizationGuard<Entity>` bean. See [Authorization](#8-authorization). |
| `readGuard`, `writeGuard` | none | Separate guards for reads and writes. Set both or neither. Can't be combined with `authorizationGuard` or `@RestlessOwnedBy`. |
| `allowAll` | `false` | Explicitly start without a guard. Without a guard and without this, startup fails. |
| `version` | `""` | API version for every route. See [`@RestlessResource`](#restlessresource). |
| `operations` | every `RestlessOperation` | Which fixed routes to register. See [`RestlessOperation`](#restlessoperation). |
| `parent` | not nested | `@RestlessParent(variable = "...", field = "...")`. See [`@RestlessParent`](#restlessparent). |
| `denyAs` | `DEFAULT` | 403 or 404 for a denied caller. See [`RestlessDenial`](#restlessdenial). |
| `readActions` | `{}` | [`ReadAction`](#readactione-r) beans, each with `@RestlessActionName`. |
| `collectionActions` | `{}` | [`CollectionAction`](#collectionactione) beans, each with `@RestlessActionName`. |
| `writeActions` | `{}` | [`WriteAction`](#writeactione-req-resp) beans, each with `@RestlessActionName`. |
| `views` | `{}` | Named-view `Mapper<Entity, ?>` beans, each with `@RestlessActionName`. |
| `hooks` | `{}` | [`RestlessLifecycleHook<Entity>`](#restlesslifecyclehooke) beans, run in the listed order. |
| `multipartCreate` | `false` | `POST {basePath}` also accepts `multipart/form-data`. |
| `bulkMode` | `DEFAULT` | See [`RestlessBulkMode`](#restlessbulkmode). |
| `bulkDedupe` | none | A [`RestlessDedupeKey`](#restlessdedupekeye-c) bean for `PARTIAL` bulk create. |
| `collectionGet` | `DEFAULT` | See [`RestlessCollectionGet`](#restlesscollectionget). |

Notes:

- `operations` controls routes, not DTO resolution. Excluding `CREATE` still requires a create
  model to resolve. Use `@RestlessReadEntity` or the manual tier when there is no create model.
- If `operations` lists only read operations and no write-only attribute is set, the generated
  resource is read-only, exactly as with `@RestlessReadEntity`.
- With only `spring-boot-restless-read` on the classpath, a `@RestlessEntity` that writes is a
  compile error naming the missing artifact.

### `@RestlessReadEntity`

`annotation.RestlessReadEntity` · on a JPA `@Entity` class · artifact `-processor`

The read-only counterpart of `@RestlessEntity`. The generated resource extends
`RestlessReadResourceHandler` and has GET routes only, so `POST`/`PUT`/`PATCH`/`DELETE` return
`405`. It needs only `{Entity}SearchDto` and `{Entity}Dto` (or a `{Entity}Mapper`), and works with
`spring-boot-restless-read` alone.

```java
@Entity
@RestlessReadEntity(basePath = "/countries", allowAll = true)
public class Country { @Id private String code; private String name; }
```

Attributes: `basePath`, `searchDto`, `dto`, `mapper`, `readDataSource`, `authorizationGuard`,
`version`, `allowAll`, `operations`, `parent`, `denyAs`, `readActions`, `collectionActions`,
`views`, `collectionGet`, `repository`. Each means exactly what it means on `@RestlessEntity`.
`operations` defaults to the five read operations. Naming a write operation there is a compile
error. `authorizationGuard` may be a [`ReadAuthorizationGuard`](#readauthorizationguarde).

### `@RestlessOwnedBy`

`annotation.RestlessOwnedBy` · on an entity next to `@RestlessEntity`/`@RestlessReadEntity` ·
artifact `-processor`

"Users manage only what they own", declared instead of written as a guard. The generated resource
gets an [`OwnershipAuthorizationGuard`](#restlessownershipguards-and-ownershipauthorizationguarde)
that scopes lists in SQL and checks every row.

Two shapes. Set exactly one:

```java
// The row stores its owner.
@RestlessOwnedBy(field = "ownerId")
public class Event { @RestlessFromPrincipal private UUID ownerId; }

// The row belongs to a parent that stores the owner.
@RestlessOwnedBy(path = "eventId", via = Event.class, ownerField = "ownerId")
public class Guest { private UUID eventId; }
```

| Attribute | Shape | Meaning |
|---|---|---|
| `field` | direct | Owner property on this entity. |
| `path` | via | Property on this entity holding the parent's id. |
| `via` | via | Parent entity class. |
| `ownerField` | via | Owner property on the parent. |

Paths may cross one association (`"event.id"`). The caller's id comes from the
[`RestlessPrincipalIdResolver`](#restlessprincipalidresolver) bean and is converted to the owner
field's type. A caller with no id owns nothing. Combining this with `authorizationGuard` or
`readGuard`/`writeGuard` is a compile error. Pair it with `denyAs = NOT_FOUND` so other people's
ids can't be probed.

### `@RestlessParent`

`annotation.RestlessParent` · used only as the `parent` attribute · artifact `-processor`
(compile time) and `-read` (runtime)

Nests a resource under a parent path variable:

```java
@RestlessEntity(basePath = "/events/{eventId}/guests",
        parent = @RestlessParent(variable = "eventId", field = "eventId"))
public class Guest { @Id private UUID id; private UUID eventId; }
```

| Attribute | Meaning |
|---|---|
| `variable` | Name of the `{variable}` segment in `basePath`. |
| `field` | Entity property that holds the parent id: a scalar (`"eventId"`) or one association hop (`"event.id"`). |

Effects: every route registers under the templated path. Reads are filtered by
`field = :pathValue` before the guard's `scope()`. Creates write the parent id from the path and
ignore any body value. A single-row or bulk write that targets another parent's row returns `404`.
Update and patch can never move a row to another parent. An unconvertible path value is a `400`.
The processor validates `field` at compile time, and the registrar checks `variable` against
`basePath` at startup.

### `RestlessOperation`

`annotation.RestlessOperation` · enum · artifact `-processor`

The values accepted by `operations`: `CREATE`, `READ_ONE`, `READ_LIST`, `READ_PAGE`,
`READ_PAGE_OVERVIEW`, `READ_PAGE_SELECT`, `UPDATE`, `DELETE_ONE`, `DELETE_ALL`. Each maps by name to
`AuthorizationGuard.Action`. `CREATE` also enables `POST /bulk`, and `UPDATE` also enables
`PUT /bulk`. `PATCH`, named actions and views are enabled by their own attributes, not here.

```java
@RestlessEntity(basePath = "/audit-log",
        operations = {RestlessOperation.READ_ONE, RestlessOperation.READ_PAGE})
```

### `@RestlessMapperExclude`

`annotation.RestlessMapperExclude` · on a DTO field · artifact `-processor`

Keeps a DTO field out of the generated mapper, so it stays at its Java default. Use it for a
computed field that other code fills in after mapping. It has no effect once you hand-write
`{Entity}Mapper`. It is unrelated to per-caller hiding. For that, see
[`@CerbosHiddenField`](#cerboshiddenfield-and-cerbosfieldmasker) or a hand-written mapper.

```java
public class TaskDto { private Long id; @RestlessMapperExclude private String displayLabel; }
```

### `RestlessEntityProcessor` and what it generates

`processor.RestlessEntityProcessor` · artifact `-processor` · registered through
`META-INF/services`, so adding the dependency is enough.

For each `@RestlessEntity`/`@RestlessReadEntity` it generates, in the entity's package:

| Generated type | When | Shape |
|---|---|---|
| `{Entity}Repository` | no such type exists and `repository` is unset | `@Repository interface ... extends SpecificationRepository<Entity, Id>` |
| `{Entity}Mapper` | no such type exists and `mapper` is unset | `@Component class ... implements Mapper<Entity, Dto>`, one `dto.setX(source.getX())` per matching property |
| `{Entity}RestlessResource` | always | `@Component @RestlessResource(...)` subclass of `RestlessResourceHandler` or `RestlessReadResourceHandler`, with every referenced bean injected through the constructor |

Mistakes it reports as compile errors include: an `@RestlessOwnedBy` with both or neither shape,
or with an unknown path; `@RestlessOwnedBy` together with an explicit guard; `readGuard` without
`writeGuard`; a `ReadAuthorizationGuard` on a resource that writes; a write operation on
`@RestlessReadEntity`; an invalid `parent`; a create/update/patch model that declares a
`@RestlessFromPrincipal` field; and a resource that writes while `-write` is missing from the
classpath. The processor is incremental (`isolating`) for Gradle.

---

## 2. Declaring a resource (runtime / manual tier)

Use these when you write the resource class yourself, for full control without code generation.

### `@RestlessResource`

`annotation.RestlessResource` · on a `RestlessReadResourceHandler` subclass · artifact `-read`

Marks a Spring bean as a resource. `RestlessRegistrar` finds it at startup and registers its
routes. Put it on the resource class, never on the entity. Generated resources carry it too.

```java
@Component
@RestlessResource(basePath = "/departments", denyAs = RestlessDenial.NOT_FOUND)
public class DepartmentRestlessResource extends RestlessResourceHandler<Department, Long> { ... }
```

| Attribute | Default | Meaning |
|---|---|---|
| `basePath` | required | Mount path. |
| `version` | `""` (no constraint) | Spring API version for every route (`"1"`, `"1.5+"`). Configure how versions are resolved with an `ApiVersionConfigurer`. List these versions with `addSupportedVersions(...)`, because `detectSupportedVersions` runs before dynamic routes are registered. |
| `allowAll` | `false` | Allow startup with no guard. Without it, a resource whose `getAuthorizationGuard()` is the default allow-all fails startup with an `IllegalStateException`. |
| `parent` | not nested | See [`@RestlessParent`](#restlessparent). |
| `denyAs` | `DEFAULT` | See [`RestlessDenial`](#restlessdenial). |
| `bulkMode` | `DEFAULT` | See [`RestlessBulkMode`](#restlessbulkmode). |
| `collectionGet` | `DEFAULT` | See [`RestlessCollectionGet`](#restlesscollectionget). |

### `RestlessReadResourceHandler<E, K>`

`resource.RestlessReadResourceHandler` · abstract class · artifact `-read`

Base class of every read-only resource. It provides the GET routes, filtering, embedding, guard
checks, nested-parent scoping and soft-delete filtering. `E` is the entity type and `K` the id
type.

You must implement:

| Method | Purpose |
|---|---|
| `protected ReadDataSource<E, K, ?> getReadDataSource()` | Loads rows. Usually `new DefaultReadDataSource<>(repository, MySearchDto.class)`. |
| `protected Mapper<E, ?> getEntityMapper()` | Response for `findOne`, `findPage`. |
| `protected Mapper<E, ?> getOverviewMapper()` | Response for `findList`, `findPageOverview`. |
| `protected Mapper<E, ?> getSelectMapper()` | Response for `findPageSelect`. |

You may override:

| Method | Default | Purpose |
|---|---|---|
| `protected ReadAuthorizationGuard<E> getAuthorizationGuard()` | allow-all | The guard. Requires `allowAll = true` if left as default. |
| `protected Specification<E> getSpecification(SearchDto)` | filter DSL | Replace the default filter. [`RestlessSpecifications`](#restlessspecificationse) helps. |
| `public Set<Action> getEnabledOperations()` | `READ_OPERATIONS` | Restrict fixed routes. |
| `public Map<String, ReadAction<E, ?>> getCustomReadActions()` | empty | Named filtered page reads. |
| `public Map<String, CollectionAction<E>> getCollectionActions()` | empty | Named summaries, exports, downloads. |
| `public Map<String, Mapper<E, ?>> getNamedViews()` | empty | Alternate single-row shapes at `{id}/{name}`. |
| `public List<RestlessRoute> getRoutes()` | derived | The full route list. Override only to add routes for your own public handler methods. |

Other public members: `READ_OPERATIONS`, `DEFAULT_MAX_LIST_SIZE` (10 000),
`DEFAULT_MAX_PAGE_SIZE` (2 000), `DEFAULT_MAX_BULK_SIZE` (1 000), `getMetadata()`,
`getCollectionGet()`, `hasExplicitAuthorizationGuard()`, and `findEmbeddedList`/`findEmbeddedOne`
(called by the embed resolver). The handler methods (`findOne`, `findList`, `findPage`,
`findPageOverview`, `findPageSelect`, `customRead`, `collectionAction`, `namedView`) and
`init`/`resolveMetadata` are `final` and called by the framework.

```java
@Component
@RestlessResource(basePath = "/countries", allowAll = true)
public class CountryResource extends RestlessReadResourceHandler<Country, String> {
    private final ReadDataSource<Country, String, CountrySearchDto> reads;
    private final CountryMapper mapper;

    public CountryResource(CountryRepository repository, CountryMapper mapper) {
        this.reads = new DefaultReadDataSource<>(repository, CountrySearchDto.class);
        this.mapper = mapper;
    }

    @Override protected ReadDataSource<Country, String, ?> getReadDataSource() { return reads; }
    @Override protected Mapper<Country, ?> getEntityMapper() { return mapper; }
    @Override protected Mapper<Country, ?> getOverviewMapper() { return mapper; }
    @Override protected Mapper<Country, ?> getSelectMapper() { return mapper; }
}
```

### `RestlessResourceHandler<E, K>`

`resource.RestlessResourceHandler` · abstract class · artifact `-write` · extends
`RestlessReadResourceHandler<E, K>`

Base class of every read-write resource. It adds create, update, patch, delete, the three bulk
writes, write actions, lifecycle hooks and multipart create.

Overridable members, in addition to the read ones:

| Method | Default | Purpose |
|---|---|---|
| `protected CreateDataSource<E, K, ?> getCreateDataSource()` | throws | Required when `CREATE` is enabled. |
| `protected UpdateDataSource<E, K, ?> getUpdateDataSource()` | throws | Required when `UPDATE` is enabled. |
| `protected DeleteDataSource<E, K, ?> getDeleteDataSource()` | throws | Required when `DELETE_ONE`/`DELETE_ALL` is enabled. |
| `public Optional<PatchDataSource<E, K, ?>> getPatchDataSource()` | empty | Present adds `PATCH {basePath}/{id}`. |
| `protected AuthorizationGuard<E> getAuthorizationGuard()` | allow-all | Narrowed to a full guard, because writes run `canAccessAfterWrite`. |
| `public Set<Action> getEnabledOperations()` | `ALL_OPERATIONS` | Restrict fixed routes. |
| `public Map<String, WriteAction<E, ?, ?>> getCustomWriteActions()` | empty | Named commands. |
| `protected List<RestlessLifecycleHook<E>> getLifecycleHooks()` | empty | Side effects around writes. |
| `public boolean acceptsMultipartCreate()` | `false` | Accept `multipart/form-data` on `POST {basePath}`. |
| `public RestlessDedupeKey<E, ?> getBulkDeduplicator()` | `null` | Duplicate detection for `PARTIAL` bulk create. |

Other public members: `ALL_OPERATIONS`, `getPatchModelType()`, `getBulkMode()`, and the `final`
handler methods `create`, `createBulk`, `update`, `updateBulk`, `patch`, `deleteById`,
`deleteAll`, `writeAction`.

Every write runs in one transaction: pre-check, load, `canAccess`, optional `If-Match`, data
source, `canAccessAfterWrite`, hooks, flush, map, `postProcessResponse`. A denial or exception at
any step rolls everything back. See the sequence diagram in the README's
[Advanced Usage](../README.md#wiring-authorization-into-the-core-component).

### `RestlessRoute`

`resource.RestlessRoute` · record · artifact `-read`

One route a resource exposes: `handlerMethodName`, `pathSuffix`, `httpMethod`, `name` (for named
routes), `requestType`, `responseType`, `multipart`. `getRoutes()` returns a list of these, and
`RestlessRegistrar` and `RestlessOpenApiCustomizer` both read from it. `RestlessRoute.of(method,
suffix, verb)` builds a fixed route. Treat it as internal API: it may change before 1.0.

### `ResourceMetadata` and `RestlessInitContext`

`resource.ResourceMetadata`, `resource.RestlessInitContext` · records · artifact `-read`

`ResourceMetadata` holds what the registrar resolves for each resource at startup: base path,
entity and id types, the create/update/delete/search model types, the three response DTO types,
the version and the parent variable and id type. Read it with `getMetadata()`.
`RestlessInitContext` bundles the collaborators `init(...)` receives. You only build one if you
construct a resource outside Spring, for example in a unit test. Both are internal and may grow
new components.

---

## 3. Behaviour enums

Each exists in `-read` and in `-processor`, under `annotation`. `DEFAULT` always means "use the
application-wide property".

### `RestlessDenial`

What a denied caller receives.

| Value | Response |
|---|---|
| `DEFAULT` | `restless.authorization.deny-as` (default `forbidden`). |
| `FORBIDDEN` | `403`: the row exists, but not for this caller. |
| `NOT_FOUND` | `404`, identical to a missing id. Use it for user-owned data. |

### `RestlessBulkMode`

How `POST {basePath}/bulk` handles bad rows.

| Value | Behaviour |
|---|---|
| `DEFAULT` | Same as `ALL_OR_NOTHING`. |
| `ALL_OR_NOTHING` | One invalid or denied row fails the request. Nothing is written. Response: array of DTOs. |
| `PARTIAL` | Each row gets its own transaction. Invalid rows and duplicates are reported, valid rows are created. Response: [`RestlessBulkResult`](#restlessbulkresult). An authorization denial or database failure still aborts the request. |

### `RestlessCollectionGet`

What a plain `GET {basePath}` returns.

| Value | Behaviour |
|---|---|
| `DEFAULT` | `restless.collection-get` (default `page`). |
| `PAGE` | A page envelope. The plain array is at `/list`. |
| `LIST` | The plain array (capped). Pages move to `/page`, and `/list` remains as an alias. |

---

## 4. Field annotations

### `@RestlessFromPrincipal`

`annotation.RestlessFromPrincipal` · on an entity field · artifact `-read`

A field the server fills from the caller on create, typically an owner column. Never read from a
body, and immutable after create.

```java
@RestlessFromPrincipal @Column(nullable = false) private String ownerUsername;
```

- On create, the value is the caller's id from
  [`RestlessPrincipalIdResolver`](#restlessprincipalidresolver), converted to the field type.
- A create with no caller is `401`.
- Update and patch can't change it. The stored value is restored before commit.
- A create, update or patch model that declares a field with the same name is a compile error.
- In a hand-written create data source, call `context.applyServerFieldsTo(entity)` before
  `save(...)` so NOT NULL columns are filled at INSERT time.

### `@RestlessImmutable`

`annotation.RestlessImmutable` · on an entity field · artifact `-read`

A field that is set on create and never changes afterwards, such as a contract number or a slug.

```java
@RestlessImmutable(onUpdate = RestlessImmutable.OnChange.REJECT) private String code;
```

| `onUpdate` | When an update or patch sends a different, non-null value |
|---|---|
| `DEFAULT` | `restless.immutable.on-update` (default `ignore`). |
| `IGNORE` | Silently keep the stored value. |
| `REJECT` | `400` naming the field. Nothing is written. |

`null` in the body counts as "not sent". The stored value always survives, whatever a data source
did.

### `@RestlessEmbed`

`embed.RestlessEmbed` · on a response DTO field · artifact `-read`

Fills a DTO field from another resource when the client asks for it with `?expand=`. The other
resource's own guard decides what is visible.

```java
public class TaskDto {
    private Long projectId;
    @RestlessEmbed(resource = ProjectRestlessResource.class, sourceField = "projectId", targetField = "id")
    private ProjectDto project;
}
// GET /tasks/7?expand=project
```

| Attribute | Default | Meaning |
|---|---|---|
| `resource` | required | The target resource bean class. |
| `sourceField` | required | Property on this resource's entity holding the join value. Dotted paths allowed (`"employee.id"`). |
| `targetField` | required | Property on the target entity to match against. Dotted paths allowed. |
| `many` | `false` | `true` for a `List<TargetDto>` field. Unpaginated, so use it only for small, bounded relations. |
| `name` | field name | The `expand=` key. |

Applies to `findOne` and named views. A denial on the target leaves the field empty (`null` or an
empty list). It never fails the outer response. A `null` source value skips the query.

---

## 5. Models and DTO contracts

Marker interfaces and small base classes. Packages `models.*`.

### `CreateModel`, `UpdateModel`, `PatchModel`

`models.CreateModel`, `models.UpdateModel`, `models.PatchModel` · marker interfaces · artifact `-write`

Request bodies for `POST`, `PUT` and `PATCH`. Bean Validation annotations on them are enforced.
The default data sources copy fields onto the entity by matching name, but never into `@Id`,
`@Version`, `@CreatedDate`/`@LastModifiedDate`, `deleted` or `@RestlessFromPrincipal` fields. The
handler also restores the nested parent field and `@RestlessImmutable` fields after any update,
whichever data source ran.
`PatchModel` fields are all optional: `null` means "leave as is", not "clear". One class may
implement both `CreateModel` and `UpdateModel`, see
[`RestlessValidationGroups`](#restlessvalidationgroups).

### `DeleteModel` and `DefaultDeleteModel`

`models.DeleteModel`, `models.DefaultDeleteModel` · artifact `-write`

The body of `POST {basePath}/bulk-delete`. `DeleteModel` has `List<String> getIds()`. Ids are
strings converted to the key type through the `ConversionService`. `DefaultDeleteModel` is the
ready-made `{"ids": [...]}` implementation, with `@NotEmpty`. You rarely need your own.

### `WriteActionRequest`

`models.WriteActionRequest` · marker interface · artifact `-write`

Request body type of a [`WriteAction`](#writeactione-req-resp). It is validated like any other
body.

### `RestlessValidationGroups`

`models.RestlessValidationGroups` · artifact `-write`

Groups `OnCreate`, `OnUpdate` and `OnPatch`. Each create body is validated with `Default` +
`OnCreate`, each update body with `Default` + `OnUpdate`, and each patch body with `Default` +
`OnPatch`. This lets one model serve several verbs:

```java
public class EventRequest implements CreateModel, UpdateModel {
    @NotBlank private String name;                                         // always required
    @NotNull(groups = RestlessValidationGroups.OnCreate.class) private String type; // only on create
}
```

### `SearchDto` and `AbstractSearchDto`

`models.SearchDto`, `models.AbstractSearchDto` · artifact `-read`

`SearchDto` is the query-parameter object of every list, page and read-action route. Its only
method is `Pageable getPageable()`. Extend `AbstractSearchDto`, which adds `page` (default `0`),
`size` (default `20`) and a repeatable `sort` (`name`, `name,desc`, `-name`, default `id` ascending).
Every other field you declare becomes a filter. See [Filtering](#12-filtering). Filter fields must
use wrapper types (`Integer`, not `int`), so that "not sent" can be told apart.

```java
@Getter @Setter
public class TaskSearchDto extends AbstractSearchDto {
    private String titleILike;
    private Boolean done;
    private List<Status> statusIn;
}
```

### `EntityDto`

`models.EntityDto` · marker interface · artifact `-read`

Conventional marker for a response DTO. It is not required.

### `PageableResponse<B>`

`models.PageableResponse` · artifact `-read`

The page envelope: `totalPages`, `totalElements`, `pageSize`, `body`. There is no current-page
field, because the client already knows which page it asked for.

### `AbstractAuditableEntity`

`models.AbstractAuditableEntity` · `@MappedSuperclass` · artifact `-read`

Extend it to get `createdDate` and `lastModifiedDate` (`Instant`) filled in by Spring Data JPA
auditing. Add `@EnableJpaAuditing` to your application. Both fields are protected from client
writes. There are no `createdBy`/`lastModifiedBy` fields: add your own `AuditorAware` for those.

---

## 6. Persistence: repositories and data sources

A data source performs one verb against the repository. The handler calls it inside the request's
transaction, after the guard's pre-checks. Every verb has a `RestlessContext`-aware overload that
defaults to the plain one. Override whichever you need.

### `SpecificationRepository<E, K>`

`repository.SpecificationRepository` · interface · artifact `-read`

`JpaRepository<E, K>` + `JpaSpecificationExecutor<E>`, annotated `@NoRepositoryBean`. Every
resource's repository must extend it. The processor generates one when it's missing.

```java
public interface TaskRepository extends SpecificationRepository<Task, Long> {}
```

### `DataSource<E, K>` and `TypedDataSource<D>`

`datasource.DataSource`, `datasource.TypedDataSource` · artifact `-read`

`DataSource` is the base of every data source. It holds the `protected specificationRepository`
and a `final flush()`. `TypedDataSource` supplies the DTO class explicitly
(`Class<D> getDtoType()`) for data sources built with `new` from a generic class, whose type
arguments are otherwise erased. The `Default*` classes implement it. Your own named subclasses
don't need to.

### `ReadDataSource<E, K, S>`

`controller.read.ReadDataSource` · abstract class · artifact `-read`

Implement `findAll(spec, pageable)`, `findAll(spec)`, `findOne(spec, id)` and `findOne(id)`.
`findAllById(ids)` defaults to repeated `findOne`. Override it for a single query.
Implementation: `DefaultReadDataSource`. Write your own only for custom fetch logic, such as a
fetch join.

### `CreateDataSource<E, K, C extends CreateModel>`

`controller.create.CreateDataSource` · abstract class · artifact `-write`

Implement `E create(C model)`, or override `create(C, RestlessContext)` to use the request.
`createAll(...)` loops over `create` by default. Override it for a batched strategy, which the
context-aware variant then calls.

```java
@Component
public class TaskCreateDataSource extends CreateDataSource<Task, Long, TaskCreateModel> {
    public TaskCreateDataSource(TaskRepository repository) { super(repository); }

    @Override
    public Task create(TaskCreateModel model, RestlessContext context) {
        Task task = new Task();
        task.setTitle(model.getTitle().strip());
        context.applyServerFieldsTo(task); // nested parent + @RestlessFromPrincipal, before INSERT
        return specificationRepository.save(task);
    }

    @Override public Task create(TaskCreateModel model) { return create(model, RestlessContext.current()); }
}
```

### `UpdateDataSource<E, K, U extends UpdateModel>`

`controller.update.UpdateDataSource` · abstract class · artifact `-write`

Implement `E update(K id, U model)`. `updateAll(Map<K, U>)` loops by default.

### `PatchDataSource<E, K, P extends PatchModel>`

`controller.patch.PatchDataSource` · abstract class · artifact `-write`

Implement `E patch(K id, P model)`. Optional: without one there is no `PATCH` route. Guarded as
`Action.PATCH`, not `UPDATE`.

### `DeleteDataSource<E, K, D extends DeleteModel>`

`controller.delete.DeleteDataSource` · abstract class · artifact `-write`

Implement `deleteById(K id)` and `deleteAll(D model)`.

### Default implementations

Package `datasource.defaults`. They copy matching properties with `BeanUtils.copyProperties` and
skip the protected fields listed under [models](#createmodel-updatemodel-patchmodel).

| Class | Artifact | Constructor | Behaviour |
|---|---|---|---|
| `DefaultReadDataSource<E, K, S>` | `-read` | `(repository, SearchDto.class)` | Delegates to the repository. `findAllById` is one query. |
| `DefaultCreateDataSource<E, K, C>` | `-write` | `(repository, Entity.class, CreateModel.class)` | Instantiates the entity (or the [`@RestlessSubtype`](#restlesssubtype) class), copies the model, applies server fields, saves. |
| `DefaultUpdateDataSource<E, K, U>` | `-write` | `(repository, UpdateModel.class)` | Loads (`404` if missing), copies, saves. |
| `DefaultPatchDataSource<E, K, P>` | `-write` | `(repository, PatchModel.class)` | Like update, but skips `null` properties. |
| `DefaultDeleteDataSource<E, K>` | `-write` | `(repository, Id.class[, conversionService])` | Hard delete. Bulk delete loads each row and deletes it. |
| `DefaultSoftDeleteDataSource<E extends SoftDeletable, K>` | `-write` | `(repository, Id.class[, conversionService])` | Sets `deleted = true` and saves. |

Pass the application's `ConversionService` to the delete data sources if your custom id
converters must apply to bulk-delete ids. For a generated `patchDataSource`, write a tiny named
subclass:

```java
@Component
public class TaskPatchDataSource extends DefaultPatchDataSource<Task, Long, TaskPatchModel> {
    public TaskPatchDataSource(TaskRepository repository) { super(repository, TaskPatchModel.class); }
}
```

### `SoftDeletable`

`datasource.SoftDeletable` · interface · artifact `-read`

`boolean isDeleted()` / `void setDeleted(boolean)`, backed by a persistent field named exactly
`deleted`. Pair it with `DefaultSoftDeleteDataSource`. Soft-deleted rows disappear from lists,
pages, read actions and collection actions. A write to one returns `404`. Reading one by id
follows `restless.soft-delete.include-in-single-read`.

---

## 7. Mapping

### `Mapper<E, D>`

`mapper.Mapper` · interface · artifact `-read`

Turns an entity into a response DTO: `D map(E source)`, plus a default `List<D> map(List<E>)`.
The response shape is always yours to decide. The processor can generate the field-copying code
for a DTO you wrote, but it never invents the DTO. Mappers are also used as named views.

```java
@Component
public class TaskMapper implements Mapper<Task, TaskDto> {
    public TaskDto map(Task t) { return new TaskDto(t.getId(), t.getTitle(), t.isDone()); }
}
```

---

## 8. Authorization

Guards decide who may do what. They depend only on `HttpServletRequest`, not on Spring Security.
Every hook defaults to allow. Override only what you restrict.

### `ReadAuthorizationGuard<E>`

`authorization.ReadAuthorizationGuard` · interface · artifact `-read`

The read hooks. Enough for a read-only resource.

| Hook | When | Return |
|---|---|---|
| `preCheck(action, name, request)` | Before any database work, on every route. | `false` → denied. |
| `scope(action, name, request)` | List, page, read-action and collection-action queries. | A `Specification` ANDed onto the query, or `null` for no restriction. |
| `canAccess(action, request, entity)` | After a single row is loaded. | `false` → denied. |
| `canAccess(action, name, request, entity)` | Same, with the action or view name. Defaults to the three-argument version. | |
| `canAccessAll(action, request, entities)` | Bulk update/delete, once for the whole batch. Defaults to looping `canAccess`. | |
| `postProcessResponse(action, name, request, entity, dto)` | After mapping a single-entity response. | The DTO to send, for example with fields masked. |

```java
@Component
public class ReportGuard implements ReadAuthorizationGuard<Report> {
    @Override
    public Specification<Report> scope(Action action, String name, HttpServletRequest request) {
        return (root, query, cb) -> cb.equal(root.get("team"), request.getHeader("X-Team"));
    }
}
```

### `AuthorizationGuard<E>`

`authorization.AuthorizationGuard` · interface · artifact `-read` · extends
`ReadAuthorizationGuard<E>`

The full guard, required by read-write resources. It adds
`canAccessAfterWrite(action, name, request, after)`, which runs after an update or patch on the
changed entity. It catches a caller who moves their own row somewhere they couldn't reach. It
defaults to `canAccess`. `AuthorizationGuard.allowAll()` / `ALLOW_ALL` is the shared no-op guard.

`AuthorizationGuard.Action` lists every action a guard can be asked about:

| Action | Route | `isWrite()` |
|---|---|---|
| `CREATE` | `POST {basePath}`, `POST /bulk` | yes |
| `READ_ONE` | `GET /{id}` | no |
| `READ_LIST` | `GET /list` (also the bulk-dedupe lookup) | no |
| `READ_PAGE` | `GET {basePath}` / `/page` | no |
| `READ_PAGE_OVERVIEW` | `GET /overview` | no |
| `READ_PAGE_SELECT` | `GET /select/async` | no |
| `UPDATE` | `PUT /{id}`, `PUT /bulk` | yes |
| `PATCH` | `PATCH /{id}` | yes |
| `DELETE_ONE` | `DELETE /{id}` | yes |
| `DELETE_ALL` | `POST /bulk-delete` | yes |
| `CUSTOM_READ` | `GET /actions/{name}` (read and collection actions) | no |
| `NAMED_VIEW` | `GET /{id}/{name}` | no |
| `WRITE_ACTION` | `POST /{id}/actions/{name}` | yes |

`PATCH`, `NAMED_VIEW` and `WRITE_ACTION` never inherit permission from `UPDATE` or `READ_ONE`.
Grant them explicitly.

```java
@Component
public class TaskGuard implements AuthorizationGuard<Task> {
    @Override
    public boolean preCheck(Action action, String name, HttpServletRequest request) {
        return request.isUserInRole("TASK_USER");
    }
    @Override
    public boolean canAccess(Action action, HttpServletRequest request, Task task) {
        return request.getUserPrincipal().getName().equals(task.getOwnerUsername());
    }
}
```

### `AuthorizationGuards`

`authorization.AuthorizationGuards` · utility · artifact `-read`

`AuthorizationGuards.split(readGuard, writeGuard)` returns one guard that sends read actions to
`readGuard` and write actions to `writeGuard`, including `scope` and `postProcessResponse`. Both
arguments are required. Splitting two `allowAll()` guards returns `allowAll()`. This is what
`readGuard`/`writeGuard` on `@RestlessEntity` generates.

```java
@Override
protected AuthorizationGuard<Ledger> getAuthorizationGuard() {
    return AuthorizationGuards.split(ledgerReadGuard, ledgerWriteGuard);
}
```

### `RestlessPrincipalIdResolver`

`authorization.RestlessPrincipalIdResolver` · functional interface · artifact `-read`

Who the caller is, as the value an owner column stores. `Object resolve(RestlessContext)`, called
at most once per request. `null` means no caller, and everything built on it treats that as a
denial. The default bean is `AUTHENTICATION_NAME`, the authenticated principal's name (JWT `sub`,
username), with anonymous counting as none. Replace the bean when owner columns hold something
else:

```java
@Bean
RestlessPrincipalIdResolver principalIdResolver(CurrentUserService users) {
    return context -> context.authentication() == null ? null : users.require().getId();
}
```

Used by `@RestlessFromPrincipal`, `@RestlessOwnedBy` and `RestlessContext.principalId()`.

### `RestlessOwnershipGuards` and `OwnershipAuthorizationGuard<E>`

`authorization.ownership.*` · artifact `-read`

`RestlessOwnershipGuards` is an auto-configured bean that builds ownership guards. It is what
`@RestlessOwnedBy` uses. Use it directly on the manual tier:

```java
protected AuthorizationGuard<Event> getAuthorizationGuard() {
    return ownershipGuards.owned(Event.class, "ownerId");
}
// or: ownershipGuards.ownedVia(Guest.class, "eventId", Event.class, "ownerId")
```

The resulting `OwnershipAuthorizationGuard` scopes lists in SQL (a subquery for `ownedVia`), checks
every loaded or created row, and caches parent ownership per request. On a nested resource whose
parent is the ownership link, it checks the path's parent once in `preCheck`. Property paths are
validated when the guard is built, so a typo fails startup.

---

## 9. Per-request context

### `RestlessContext`

`resource.RestlessContext` · final class · artifact `-read`

Request data for data sources, guards, hooks and actions. It is passed to the context-aware
data-source overloads, and is available anywhere during a request through
`RestlessContext.current()` (which returns `null` outside a request).

| Member | Returns |
|---|---|
| `static current()` | The context of the request on this thread, or `null`. |
| `pathVariables()` | Raw path variables. |
| `pathVariable(name, type)` | One path variable converted with the app's `ConversionService`. `400` if it can't be converted. |
| `request()` | The `HttpServletRequest`. |
| `authentication()` | Spring Security `Authentication` as `Object` (cast it yourself), or `null`. |
| `action()`, `customActionName()` | The action being checked and its name, if any. |
| `principalId()`, `principalId(type)` | The caller id from `RestlessPrincipalIdResolver`, optionally converted. `null` if absent or not convertible. |
| `parentField()`, `parentValue()` | Nested resources: the parent field path and the converted path value. |
| `applyParentTo(entity)` | Writes the path's parent id onto a new entity. |
| `applyServerFieldsTo(entity)` | `applyParentTo` plus every `@RestlessFromPrincipal` field. Throws `401` with no caller. |

```java
UUID eventId = RestlessContext.current().pathVariable("eventId", UUID.class);
```

---

## 10. Actions, views, files and hooks

On the generated tier, list action, view and hook beans on `@RestlessEntity` and name actions and
views with `@RestlessActionName`. On the manual tier, return them from the matching
`get...()` method.

### `@RestlessActionName`

`annotation.RestlessActionName` · on an action or view bean class · artifact `-read`

The last path segment the bean is served at: `@RestlessActionName("stats")` →
`GET {basePath}/actions/stats`. Required for every bean listed in `readActions`,
`collectionActions`, `writeActions` or `views`. A missing or duplicate name fails startup.

### `RestlessActions`

`resource.RestlessActions` · utility · artifact `-read`

`RestlessActions.byName(beans...)` builds the name-keyed map from `@RestlessActionName` beans.
Generated resources use it, and you can use it in a hand-wired `getCustomReadActions()` and
similar methods.

### `ReadAction<E, R>`

`resource.ReadAction` · interface · artifact `-read`

A named, paged, filtered read with its own search DTO: `GET {basePath}/actions/{name}`.

```java
@Component
@RestlessActionName("by-email-domain")
public class ByEmailDomain implements ReadAction<Employee, DomainSearchDto> {
    public Class<DomainSearchDto> getSearchDtoType() { return DomainSearchDto.class; }
    public Specification<Employee> buildSpecification(DomainSearchDto s) {
        return RestlessSpecifications.<Employee>builder().like("email", "%@" + s.getDomain()).build();
    }
}
```

The specification is ANDed with the guard's `scope`, the parent filter and the soft-delete filter.
Guard action: `CUSTOM_READ` with the name.

### `CollectionAction<E>`

`resource.CollectionAction` · functional interface · artifact `-read`

A named read over the whole collection that isn't a page of rows, such as a count, summary,
export or download link: `GET {basePath}/actions/{name}`. It receives a `Specification` of exactly
the rows the caller may see and runs in a read-only transaction. It may return any JSON value or
a `RestlessBinary`. It shares the `/actions/` namespace with read actions.

```java
@Component
@RestlessActionName("stats")
public class NotebookStats implements CollectionAction<Notebook> {
    private final NotebookRepository repository;
    public NotebookStats(NotebookRepository repository) { this.repository = repository; }
    public Object execute(Specification<Notebook> visibleRows, RestlessContext context) {
        return Map.of("count", repository.count(visibleRows));
    }
}
```

### Named views

A named view is any `Mapper<E, ?>` bean, served at `GET {basePath}/{id}/{name}`. It returns a
different shape of the same row, and may return a `RestlessBinary`. Guard action: `NAMED_VIEW`
with the name. Use `canAccess(action, name, request, entity)` to tell views apart.

### `WriteAction<E, Req, Resp>`

`resource.WriteAction` · interface · artifact `-write`

A named command on one row: `POST {basePath}/{id}/actions/{name}`. Use it for state transitions
with rules that a plain `PUT` can't express.

| Method | Purpose |
|---|---|
| `Class<Req> getRequestType()` | Body type, a `WriteActionRequest`. Validated. |
| `Class<Resp> getResponseType()` | Response type, used for OpenAPI. |
| `Resp execute(E entity, Req request)` | Runs on the loaded, guard-checked entity in one transaction. Throw `ResponseStatusException` to reject. |

Request flow: `preCheck(WRITE_ACTION, name)` → validate the body → load → `canAccess` → optional
`If-Match` → `execute` → commit → `200`. The action does its own persistence and its own response
mapping. It may return a `RestlessBinary`.

```java
@Component
@RestlessActionName("promote")
public class Promote implements WriteAction<Employee, PromoteRequest, EmployeeDto> {
    // getRequestType/getResponseType ...
    public EmployeeDto execute(Employee e, PromoteRequest r) {
        if (!r.getNewJobTitle().isPromotionFrom(e.getJobTitle())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "not a promotion");
        }
        e.setJobTitle(r.getNewJobTitle());
        return mapper.map(repository.save(e));
    }
}
```

### `RestlessBinary`

`resource.RestlessBinary` · final class · artifact `-read`

A non-JSON response such as a PDF, an image or a ZIP. Return it from a named view, collection
action or write action. It is written straight to the response inside the route's transaction.

| Factory / method | Purpose |
|---|---|
| `of(contentType, Writer)` | Streamed body (`out -> ...`). Best for large or generated content. |
| `of(contentType, byte[])` | In-memory body with `Content-Length`. |
| `of(contentType, Resource)` | A Spring `Resource`. |
| `.filename(name)` | `Content-Disposition` file name. |
| `.inline()` | Display in the browser instead of downloading (the default is `attachment`). |

```java
return RestlessBinary.of("application/pdf", out -> renderer.render(event, out))
        .filename("seating-chart.pdf").inline();
```

### `RestlessLifecycleHook<E>`

`resource.RestlessLifecycleHook` · interface · artifact `-write`

Side effects around writes, such as deleting stored files, publishing events or clearing caches.
All methods are default no-ops and receive the `RestlessContext`:

| Method | When |
|---|---|
| `afterCreate(created, ctx)` | After create and authorization, before flush. Server fields are already set. |
| `beforeUpdate(existing, ctx)` | Before the update/patch data source runs. |
| `afterUpdate(updated, ctx)` | After update/patch and authorization, before flush. |
| `beforeDelete(existing, ctx)` | Before the delete, while related data still exists. |
| `afterDelete(deleted, ctx)` | After the delete is flushed. The entity is detached. |

Hooks run inside the write's transaction, only for authorized rows, and once per row on bulk
routes. An exception rolls the write back. Work outside the database can't be rolled back, so do it
last.

### `@RestlessSubtype`

`annotation.RestlessSubtype` · on a create model class · artifact `-write`

Polymorphic create: tells `DefaultCreateDataSource` which entity subclass to instantiate for this
create-model subtype. It is usually paired with Jackson's `@JsonTypeInfo`.

```java
@RestlessSubtype(Circle.class)
public class CircleCreateModel extends ShapeCreateModel { private double radius; }
```

The subtype must extend the resource's entity type. A mismatch is a `500` with a clear message.

---

## 11. Bulk writes

Every write resource has `POST /bulk`, `PUT /bulk` (body `{"<id>": {...}}`) and
`POST /bulk-delete` (body `{"ids": [...]}`), capped by `restless.bulk.max-size`. Bulk
update/delete load every target in one query and guard-check them in one `canAccessAll` call before
writing anything.

### `RestlessBulkResult`

`resource.RestlessBulkResult` · record · artifact `-write`

Response of a `PARTIAL` bulk create: `created`, `skipped`, `errors` (a list of
`RowError(index, message)` with 0-based indexes) and `items` (the created DTOs).

```json
{"created":1,"skipped":1,"errors":[{"index":1,"message":"text: must not be blank"}],"items":[...]}
```

### `RestlessDedupeKey<E, C>`

`resource.RestlessDedupeKey` · interface · artifact `-write`

Duplicate detection for `PARTIAL` bulk create. `keyOfModel(C)` and `keyOfExisting(E)` must
produce equal keys for the same thing. A new row is skipped when its key matches a row the caller
can already see (under the same parent) or an earlier row in the batch. Return `null` for "never a
duplicate". Clients can disable deduplication with `?dedupe=false`.

```java
@Component
public class NoteTextKey implements RestlessDedupeKey<Note, NoteCreateModel> {
    public Object keyOfModel(NoteCreateModel m) { return m.getText().strip().toLowerCase(); }
    public Object keyOfExisting(Note n) { return n.getText().strip().toLowerCase(); }
}
```

---

## 12. Filtering

### Default filter and `FilterOperator`

`filter.FilterOperator` · enum · artifact `-read`

Every non-null, non-blank field of the search DTO becomes a predicate, ANDed together. A plain
field name means equality. A field name ending in an operator suffix applies that operator to the
property before the suffix:

| Suffix | Operator | Valid on | Example |
|---|---|---|---|
| *(none)* | `=` | any | `?status=OPEN` |
| `Gte` / `Lte` / `Gt` / `Lt` | `>=` / `<=` / `>` / `<` | `Comparable` | `?salaryGte=1000` |
| `Like` | contains | strings | `?titleLike=urgent` |
| `ILike` | contains, case-insensitive | strings | `?titleILike=urgent` |
| `StartsWith` | prefix (index-friendly) | strings | `?codeStartsWith=EU` |
| `Ne` | `<>` | any | `?statusNe=CLOSED` |
| `In` | `IN (...)` | DTO field must be a `Collection` | `?statusIn=OPEN&statusIn=PENDING` |

Wildcards (`%`, `_`, `\`) in client values are escaped. All bindings are validated once at
startup: an unknown property, an unsupported operator, `In` on a non-collection field, or a
primitive filter field fails startup. If the full field name is itself a real property (`checkIn`),
it is treated as equality. Public API: `forFieldName(name)`, `basePropertyOf(name)`,
`supports(type)`, `predicate(cb, path, value)`.

### `RestlessSpecifications<E>`

`filter.RestlessSpecifications` · fluent builder · artifact `-read`

For hand-written `getSpecification()` overrides, read actions and guard `scope()` methods. Each
method skips a `null` or blank value, so you don't need "is this set?" checks.

| Method | Predicate |
|---|---|
| `eq`, `ne` | `=`, `<>` |
| `like(property, pattern)` | `LIKE pattern` (include your own `%`) |
| `gte`, `lte`, `gt`, `lt` | comparisons |
| `between(property, from, to)` | only when both bounds are set |
| `in(property, values)` | `IN` (empty means skip) |
| `ownedBy(ownerProperty, principalId)` | owner equality. A `null` principal matches nothing. |
| `ownedVia(link, ParentType, parentId, parentOwner, principalId)` | ownership through a parent, as a subquery |
| `build()` | the ANDed `Specification` (matches everything when nothing was added) |

```java
@Override
protected Specification<Employee> getSpecification(SearchDto dto) {
    EmployeeSearchDto s = (EmployeeSearchDto) dto;
    return RestlessSpecifications.<Employee>builder()
            .eq("departmentId", s.getDepartmentId())
            .between("salary", s.getMinSalary(), s.getMaxSalary())
            .build();
}
```

---

## 13. Embedding

### `RestlessEmbedResolver`

`embed.RestlessEmbedResolver` · auto-configured bean · artifact `-read`

Fills [`@RestlessEmbed`](#restlessembed) fields after mapping, based on `?expand=`.
`resolve(dto, sourceEntity, request)` is called by the handler. Replace the bean only to change
how embedding works. `RestlessEmbedResolver.NONE` is the no-op used outside Spring.

---

## 14. Errors

### `RestlessExceptionHandler`

`error.RestlessExceptionHandler` · `@RestControllerAdvice` · artifact `-read`

Gives every Restless route one RFC 9457 error body (`application/problem+json`):

```json
{
  "type": "https://github.com/cristivoicu/spring-boot-restless/problems/validation-failed",
  "title": "Bad Request", "status": 400, "detail": "Validation failed",
  "instance": "/tasks", "timestamp": "2026-10-24T10:00:00Z",
  "errors": [{"field": "title", "message": "must not be blank", "code": "NotBlank"}]
}
```

| Exception | Status |
|---|---|
| `ResponseStatusException` | its own status |
| `MethodArgumentNotValidException` (validation) | `400`, with `errors` |
| `HttpMessageNotReadableException` (malformed JSON) | `400` |
| `MethodArgumentTypeMismatchException` | `400` |
| `OptimisticLockingFailureException` | `409` |
| `DataIntegrityViolationException` | `409`, generic message (never the SQL) |
| any Spring `ErrorResponse` | its own status |
| anything else | `500`, logged |

It only handles beans that implement `RestlessErrorScope`, so your own controllers keep their
error handling. It has the lowest precedence, so your own `@ControllerAdvice` wins for the same
exception type.

### `RestlessErrorBodyFactory`

`error.RestlessErrorBodyFactory` · functional interface · artifact `-read`

Register one bean to replace the `ProblemDetail` body with your own error shape. Status codes and
messages stay the same.

```java
@Bean
RestlessErrorBodyFactory apiErrors() {
    return (status, message, fieldErrors, request) ->
            ApiError.of(status.value(), message, request.getRequestURI());
}
```

### `RestlessFieldError`

`error.RestlessFieldError` · record · artifact `-read`

One validation violation in `errors`: `field`, `message`, `code` (the constraint's short name,
such as `NotBlank`).

### `RestlessErrorScope`

`error.RestlessErrorScope` · marker interface · artifact `-read`

Implemented by `RestlessReadResourceHandler` and the four `*Controller` base classes. It limits
`RestlessExceptionHandler` to framework beans. Implement it on your own controller only if you
want the same error body there.

---

## 15. Infrastructure and configuration

### `RestlessAutoConfiguration`

`autoconfigure.RestlessAutoConfiguration` · artifact `-read`

Registered through `AutoConfiguration.imports`, so no `@ComponentScan` is needed. Every bean is
`@ConditionalOnMissingBean`, so declaring your own bean replaces it.

| Bean | Condition | Purpose |
|---|---|---|
| `RestlessRegistrar` | always | Registers every resource's routes. |
| `RestlessEmbedResolver` | always | `?expand=` support. |
| `RestlessPrincipalIdResolver` | always | Default: authentication name. |
| `RestlessOwnershipGuards` | always (needs an `EntityManager`) | Backs `@RestlessOwnedBy`. |
| `RestlessExceptionHandler` | always | Error bodies. Uses a `RestlessErrorBodyFactory` bean if present. |
| `RestlessAuthorizationMetrics` | Micrometer on the classpath | Denial counter. |
| `RestlessOpenApiCustomizer` | springdoc on the classpath | OpenAPI documentation. |

### `RestlessProperties`

`autoconfigure.RestlessProperties` · `@ConfigurationProperties("restless")` · artifact `-read`

| Property | Default | Meaning |
|---|---|---|
| `restless.list.max-size` | `10000` | Cap for `/list`. Truncated responses carry `X-Restless-List-Truncated: true`. |
| `restless.page.max-size` | `2000` | Maximum `size`. Over the cap is a `400`. |
| `restless.bulk.max-size` | `1000` | Maximum items per bulk request. Over the cap is a `400`. |
| `restless.soft-delete.include-in-single-read` | `true` | Whether a soft-deleted row is still returned by id. |
| `restless.authorization.deny-as` | `forbidden` | Default for `denyAs = DEFAULT`: `forbidden` or `not-found`. |
| `restless.immutable.on-update` | `ignore` | Default for `@RestlessImmutable(onUpdate = DEFAULT)`: `ignore` or `reject`. |
| `restless.collection-get` | `page` | Default for `collectionGet = DEFAULT`: `page` or `list`. |

### `RestlessRegistrar`

`registry.RestlessRegistrar` · `SmartInitializingSingleton` · artifact `-read`

At startup, finds every `@RestlessResource` bean, resolves its metadata, validates it (guard or
`allowAll`, parent variable, filter bindings, duplicate paths) and registers each route from
`getRoutes()` with Spring MVC. You don't call it. Its internals aren't API.

### `RestlessRoutes`

`registry.RestlessRoutes` · artifact `-read`

The table of fixed routes (`RestlessRoutes.FIXED`, a list of
`RouteDefinition(handlerMethodName, pathSuffix, httpMethod, operation)`) and
`pathSuffixOf(route, collectionGet)`. It is shared by registration and OpenAPI generation, and is
useful if you write tooling that lists routes.

### `RestlessOpenApiCustomizer`

`openapi.RestlessOpenApiCustomizer` · springdoc `GlobalOpenApiCustomizer` · artifact `-read`

Adds every dynamically registered route to `/v3/api-docs` and Swagger UI, with request, response
and query-parameter schemas, nested path parameters, multipart create and `x-api-version`.
Springdoc can't see these routes on its own. It is active when
`springdoc-openapi-starter-webmvc-ui` is on the classpath.

### `RestlessAuthorizationMetrics`

`metrics.RestlessAuthorizationMetrics` · artifact `-read`

Increments the Micrometer counter `restless.authorization.denials` with the tags `resource`,
`action` and `hook` (`preCheck`, `canAccess`, `canAccessAfterWrite`) on every denial. Active when
Micrometer is present (for example through `spring-boot-starter-actuator`). `NONE` is the no-op.

---

## 16. Hand-subclassed controllers (legacy tier)

`controller.create.CreateController`, `controller.read.ReadController`,
`controller.update.UpdateController`, `controller.delete.DeleteController` · artifacts `-read`
(read) and `-write` (others)

Abstract `@RestController` building blocks from before dynamic registration. You subclass one per
verb and annotate the subclass with `@RestController @RequestMapping(...)`. They use the same data
sources, mappers and error body, but **not** guards, bulk writes, `PATCH`, actions or the filter
DSL defaults. Prefer `RestlessResourceHandler` for new code. These remain for existing users.

```java
@RestController @RequestMapping("/legacy-employees")
public class EmployeeReadController extends ReadController<Employee, Long, EmployeeSearchDto, EmployeeDto> { ... }
```

---

## 17. Cerbos module

Artifact `spring-boot-restless-cerbos`, package `cerbos`. It backs guards with
[Cerbos](https://www.cerbos.dev/) policies and needs a running PDP.

### `CerbosAuthorizationGuard<E>`

A full `AuthorizationGuard` that asks Cerbos:

| Hook | Cerbos call |
|---|---|
| `preCheck` | `check()` against a placeholder resource with no attributes |
| `canAccess` | `check()` against the loaded entity's id and attributes |
| `scope` | `plan()`, translated into a JPA `Specification` |
| `postProcessResponse` | masks `@CerbosHiddenField` fields using the policy output |

Constructors (each also has a variant with a custom `actionNaming` and a
`principalAttributesExtender`):

```java
new CerbosAuthorizationGuard<>(client, "task", Task::getId,
        task -> Map.of("ownerUsername", AttributeValue.stringValue(task.getOwnerUsername())));

// attributes computed from the DTO:
new CerbosAuthorizationGuard<>(client, "task", Task::getId, taskMapper,
        (task, dto) -> Map.of("label", AttributeValue.stringValue(dto.getLabel())));
```

It fails closed: an unreachable or slow PDP (past `cerbos.client.timeout`) means deny. Because
`preCheck` sends no resource attributes, guard attribute conditions with
`!has(request.resource.attr.x) || ...`. For a generated resource, wrap it in a small named
`@Component` that implements `AuthorizationGuard<Entity>` and delegates to it.

### `CerbosResourceAttributesMapper<E>` and `CerbosDtoResourceAttributesMapper<E, D>`

Functional interfaces that produce the resource attributes a policy sees.
`CerbosResourceAttributesMapper.reflective(Entity.class)` exposes every simple field.
`CerbosResourceAttributesMapper.none()` exposes nothing. The DTO variant also receives the mapped
DTO, for computed attributes.

### `CerbosActionNaming`

Maps an `Action` to a policy action name: the lower-case enum name (`read_one`, `create`, ...), or
the action's own name for `CUSTOM_READ`, `NAMED_VIEW` and `WRITE_ACTION`. `DEFAULT` is the
`BiFunction`. Pass your own to match existing policies.

### `CerbosPrincipalResolver`

Builds a Cerbos `Principal` from the Spring Security context. For a `JwtAuthenticationToken`, the
subject becomes the id and the claims become attributes. `resolve()` throws `401` when no one is
authenticated. `tryResolve()` returns an `Optional`.

### `@CerbosHiddenField` and `CerbosFieldMasker`

`@CerbosHiddenField("key")` marks a DTO field (a reference type) that a policy may hide. When the
activated rule's `output` contains the key (or the field name), the field is set to `null`.

```yaml
- actions: ["read_one"]
  effect: EFFECT_ALLOW
  roles: ["manager"]
  output:
    when:
      ruleActivated: 'request.principal.attr.canViewSalary == true ? [] : ["salary"]'
```

`CerbosAuthorizationGuard` masks single-entity responses automatically. For lists and pages, call
`CerbosFieldMasker.maskAll(client, principal, kind, action, dtos, idExtractor, attributes)` in
your mapper. It makes one batched RPC. `mask(...)` overloads exist for single DTOs. A missing rule
masks nothing: masking refines row access, it doesn't replace it.

### `CerbosHealthIndicator` and `CerbosAutoConfiguration`

`CerbosAutoConfiguration` creates a `CerbosBlockingClient` from `cerbos.client.target` (default
`localhost:3593`), `cerbos.client.plaintext` (default `true`) and `cerbos.client.timeout` (default
`3s`), plus a `CerbosHealthIndicator` (`/actuator/health` reports whether the PDP answers) when
Actuator is present. Declare your own `CerbosBlockingClient` bean to replace the client.

---

## 18. Test module

Artifact `spring-boot-restless-test`, package `test`. Use it with MockMvc.

### `RestlessErrorAssertions`

```java
mockMvc.perform(post("/tasks").contentType(JSON).content("{}"))
        .andExpect(RestlessErrorAssertions.restlessError(HttpStatus.BAD_REQUEST));
// or restlessError(status, "exact detail message")
```

Checks the status, `application/problem+json` and every `ProblemDetail` member.

### `RestlessPageAssertions`

```java
mockMvc.perform(get("/tasks")).andExpect(RestlessPageAssertions.restlessPage(42, 20));
// or restlessPage() for shape only
```

Checks the page envelope, optionally with exact `totalElements` and `pageSize`.

### `RestlessOwnershipContract`

Proves "a stranger can't touch the owner's rows" across every route of a resource in one call:

```java
RestlessOwnershipContract.of(mockMvc, "/notebooks/{notebookId}/notes")
        .pathVariable("notebookId", alicesNotebook)
        .owner(user("alice")).stranger(user("bob"))
        .createBody("{\"text\":\"secret\"}").updateBody("{\"text\":\"changed\"}")
        .denialStatus(HttpStatus.NOT_FOUND) // default; HttpStatus.FORBIDDEN for denyAs = FORBIDDEN
        .verify();
```

The owner creates a row. The stranger must then be denied `GET`, `PUT`, `DELETE` and bulk-delete
on it, must not see it in `/list`, and must not be able to create under the owner's parent. Other
options: `idPath("$.uuid")` and `withoutBulkDelete()`. Run it outside a test-wide transaction.

---

## Index (A–Z)

| Component | Kind | Artifact | Section |
|---|---|---|---|
| `AbstractAuditableEntity` | class | read | [5](#abstractauditableentity) |
| `AbstractSearchDto` | class | read | [5](#searchdto-and-abstractsearchdto) |
| `AuthorizationGuard` (+ `Action`) | interface | read | [8](#authorizationguarde) |
| `AuthorizationGuards` | utility | read | [8](#authorizationguards) |
| `CerbosActionNaming` | utility | cerbos | [17](#cerbosactionnaming) |
| `CerbosAuthorizationGuard` | class | cerbos | [17](#cerbosauthorizationguarde) |
| `CerbosAutoConfiguration` | auto-config | cerbos | [17](#cerboshealthindicator-and-cerbosautoconfiguration) |
| `CerbosDtoResourceAttributesMapper` | interface | cerbos | [17](#cerbosresourceattributesmappere-and-cerbosdtoresourceattributesmappere-d) |
| `CerbosFieldMasker` | utility | cerbos | [17](#cerboshiddenfield-and-cerbosfieldmasker) |
| `CerbosHealthIndicator` | class | cerbos | [17](#cerboshealthindicator-and-cerbosautoconfiguration) |
| `@CerbosHiddenField` | annotation | cerbos | [17](#cerboshiddenfield-and-cerbosfieldmasker) |
| `CerbosPrincipalResolver` | utility | cerbos | [17](#cerbosprincipalresolver) |
| `CerbosResourceAttributesMapper` | interface | cerbos | [17](#cerbosresourceattributesmappere-and-cerbosdtoresourceattributesmappere-d) |
| `CollectionAction` | interface | read | [10](#collectionactione) |
| `CreateController` | class | write | [16](#16-hand-subclassed-controllers-legacy-tier) |
| `CreateDataSource` | class | write | [6](#createdatasourcee-k-c-extends-createmodel) |
| `CreateModel` | interface | write | [5](#createmodel-updatemodel-patchmodel) |
| `DataSource` | class | read | [6](#datasourcee-k-and-typeddatasourced) |
| `DefaultCreateDataSource` | class | write | [6](#default-implementations) |
| `DefaultDeleteDataSource` | class | write | [6](#default-implementations) |
| `DefaultDeleteModel` | class | write | [5](#deletemodel-and-defaultdeletemodel) |
| `DefaultPatchDataSource` | class | write | [6](#default-implementations) |
| `DefaultReadDataSource` | class | read | [6](#default-implementations) |
| `DefaultSoftDeleteDataSource` | class | write | [6](#default-implementations) |
| `DefaultUpdateDataSource` | class | write | [6](#default-implementations) |
| `DeleteController` | class | write | [16](#16-hand-subclassed-controllers-legacy-tier) |
| `DeleteDataSource` | class | write | [6](#deletedatasourcee-k-d-extends-deletemodel) |
| `DeleteModel` | interface | write | [5](#deletemodel-and-defaultdeletemodel) |
| `EntityDto` | interface | read | [5](#entitydto) |
| `FilterOperator` | enum | read | [12](#default-filter-and-filteroperator) |
| `Mapper` | interface | read | [7](#mappere-d) |
| `OwnershipAuthorizationGuard` | class | read | [8](#restlessownershipguards-and-ownershipauthorizationguarde) |
| `PageableResponse` | class | read | [5](#pageableresponseb) |
| `PatchDataSource` | class | write | [6](#patchdatasourcee-k-p-extends-patchmodel) |
| `PatchModel` | interface | write | [5](#createmodel-updatemodel-patchmodel) |
| `ReadAction` | interface | read | [10](#readactione-r) |
| `ReadAuthorizationGuard` | interface | read | [8](#readauthorizationguarde) |
| `ReadController` | class | read | [16](#16-hand-subclassed-controllers-legacy-tier) |
| `ReadDataSource` | class | read | [6](#readdatasourcee-k-s) |
| `ResourceMetadata` | record | read | [2](#resourcemetadata-and-restlessinitcontext) |
| `@RestlessActionName` | annotation | read | [10](#restlessactionname) |
| `RestlessActions` | utility | read | [10](#restlessactions) |
| `RestlessAuthorizationMetrics` | class | read | [15](#restlessauthorizationmetrics) |
| `RestlessAutoConfiguration` | auto-config | read | [15](#restlessautoconfiguration) |
| `RestlessBinary` | class | read | [10](#restlessbinary) |
| `RestlessBulkMode` | enum | read, processor | [3](#restlessbulkmode) |
| `RestlessBulkResult` | record | write | [11](#restlessbulkresult) |
| `RestlessCollectionGet` | enum | read, processor | [3](#restlesscollectionget) |
| `RestlessContext` | class | read | [9](#restlesscontext) |
| `RestlessDedupeKey` | interface | write | [11](#restlessdedupekeye-c) |
| `RestlessDenial` | enum | read, processor | [3](#restlessdenial) |
| `@RestlessEmbed` | annotation | read | [4](#restlessembed) |
| `RestlessEmbedResolver` | class | read | [13](#restlessembedresolver) |
| `@RestlessEntity` | annotation | processor | [1](#restlessentity) |
| `RestlessEntityProcessor` | processor | processor | [1](#restlessentityprocessor-and-what-it-generates) |
| `RestlessErrorAssertions` | utility | test | [18](#restlesserrorassertions) |
| `RestlessErrorBodyFactory` | interface | read | [14](#restlesserrorbodyfactory) |
| `RestlessErrorScope` | interface | read | [14](#restlesserrorscope) |
| `RestlessExceptionHandler` | advice | read | [14](#restlessexceptionhandler) |
| `RestlessFieldError` | record | read | [14](#restlessfielderror) |
| `@RestlessFromPrincipal` | annotation | read | [4](#restlessfromprincipal) |
| `@RestlessImmutable` | annotation | read | [4](#restlessimmutable) |
| `RestlessInitContext` | record | read | [2](#resourcemetadata-and-restlessinitcontext) |
| `RestlessLifecycleHook` | interface | write | [10](#restlesslifecyclehooke) |
| `@RestlessMapperExclude` | annotation | processor | [1](#restlessmapperexclude) |
| `RestlessOpenApiCustomizer` | class | read | [15](#restlessopenapicustomizer) |
| `RestlessOperation` | enum | processor | [1](#restlessoperation) |
| `@RestlessOwnedBy` | annotation | processor | [1](#restlessownedby) |
| `RestlessOwnershipContract` | utility | test | [18](#restlessownershipcontract) |
| `RestlessOwnershipGuards` | class | read | [8](#restlessownershipguards-and-ownershipauthorizationguarde) |
| `RestlessPageAssertions` | utility | test | [18](#restlesspageassertions) |
| `@RestlessParent` | annotation | read, processor | [1](#restlessparent) |
| `RestlessPrincipalIdResolver` | interface | read | [8](#restlessprincipalidresolver) |
| `RestlessProperties` | properties | read | [15](#restlessproperties) |
| `@RestlessReadEntity` | annotation | processor | [1](#restlessreadentity) |
| `RestlessReadResourceHandler` | class | read | [2](#restlessreadresourcehandlere-k) |
| `RestlessRegistrar` | class | read | [15](#restlessregistrar) |
| `@RestlessResource` | annotation | read | [2](#restlessresource) |
| `RestlessResourceHandler` | class | write | [2](#restlessresourcehandlere-k) |
| `RestlessRoute` | record | read | [2](#restlessroute) |
| `RestlessRoutes` | utility | read | [15](#restlessroutes) |
| `RestlessSpecifications` | builder | read | [12](#restlessspecificationse) |
| `@RestlessSubtype` | annotation | write | [10](#restlesssubtype) |
| `RestlessValidationGroups` | class | write | [5](#restlessvalidationgroups) |
| `SearchDto` | interface | read | [5](#searchdto-and-abstractsearchdto) |
| `SoftDeletable` | interface | read | [6](#softdeletable) |
| `SpecificationRepository` | interface | read | [6](#specificationrepositorye-k) |
| `TypedDataSource` | interface | read | [6](#datasourcee-k-and-typeddatasourced) |
| `UpdateController` | class | write | [16](#16-hand-subclassed-controllers-legacy-tier) |
| `UpdateDataSource` | class | write | [6](#updatedatasourcee-k-u-extends-updatemodel) |
| `UpdateModel` | interface | write | [5](#createmodel-updatemodel-patchmodel) |
| `WriteAction` | interface | write | [10](#writeactione-req-resp) |
| `WriteActionRequest` | interface | write | [5](#writeactionrequest) |
