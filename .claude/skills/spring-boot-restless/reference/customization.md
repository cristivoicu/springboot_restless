# Customization: generated-tier overrides and the manual tier

## `@RestlessEntity` attributes (generated tier)

Every attribute defaults to "use the naming convention" or "use the framework's reflective
default" — set one explicitly only where that isn't enough. All are `Class<?>` (name one concrete
class, never a parameterized type):

| Attribute | Points at | Default when unset |
|---|---|---|
| `createModel` / `updateModel` / `searchDto` / `dto` | A DTO class that doesn't follow the naming convention | `{Entity}{Suffix}` by convention |
| `mapper` | A hand-written `Mapper<Entity, ?>` `@Component` | Generated, one explicit `dto.setX(source.getX())` per matched field (not reflection) |
| `createDataSource` / `readDataSource` / `updateDataSource` / `deleteDataSource` | A hand-written `@Component` for that one verb's entity-specific logic (computed fields, related lookups, ...) | `Default*DataSource` |
| `authorizationGuard` | A hand-written `AuthorizationGuard<Entity>` `@Component` | None — resource refuses to start unless `allowAll = true` |
| `patchDataSource` | A hand-written `PatchDataSource<Entity, Id, ?>` `@Component` to add `PATCH` | No `PATCH` route at all (opt-in, unlike the four CUD verbs) |
| `version` | — (forwarded verbatim) | No version constraint on this resource's routes |
| `operations` | A `RestlessOperation[]` subset | Every fixed route (`ALL_OPERATIONS`) |
| `allowAll` | — (boolean) | `false` — startup fails fast with no guard configured |

Example, pointing several at once:

```java
@RestlessEntity(basePath = "/things", createDataSource = ThingCreateDataSource.class,
        authorizationGuard = ThingAuthorizationGuard.class, version = "1",
        operations = {RestlessOperation.READ_ONE, RestlessOperation.READ_LIST, RestlessOperation.READ_PAGE})
```

`operations` governs routing only, not DTO resolution — `createModel`/`updateModel` still need to
resolve to something even with `CREATE`/`UPDATE` excluded from `operations`. A resource that
should need no `{Entity}CreateModel` at all belongs on the manual tier instead.

## Manual tier — hand-wire `RestlessResourceHandler` directly

Needed for: a named custom read action, a write command, a named view, avoiding the `processor`
module dependency entirely, or avoiding a delegating-bean wrapper for a generic guard
implementation.

1. Entity + DTOs + `{Entity}Repository extends SpecificationRepository<{Entity}, Long>` + a
   `Mapper<{Entity}, {Entity}Dto>` — all hand-written, same as the codeless tier's DTOs.
2. Resource bean:

```java
@Component
@RestlessResource(basePath = "/things")
public class ThingRestlessResource extends RestlessResourceHandler<Thing, Long> {

    private final ThingRepository repository;
    private final Mapper<Thing, ThingDto> mapper;

    public ThingRestlessResource(ThingRepository repository, Mapper<Thing, ThingDto> mapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.createDataSource = new DefaultCreateDataSource<>(repository, ThingCreateModel.class);
        this.updateDataSource = new DefaultUpdateDataSource<>(repository, ThingUpdateModel.class);
        this.deleteDataSource = new DefaultDeleteDataSource<>(repository);
        this.readDataSource = new DefaultReadDataSource<>(repository);
    }

    @Override
    protected AuthorizationGuard<Thing> getAuthorizationGuard() { /* real guard, or allowAll() */ }

    @Override
    public Map<String, ReadAction<Thing, ?>> getCustomReadActions() { /* optional */ }

    @Override
    public Map<String, WriteAction<Thing, ?, ?>> getCustomWriteActions() { /* optional, see reference/bulk-and-write-commands.md */ }

    @Override
    public Map<String, Mapper<Thing, ?>> getNamedViews() { /* optional */ }
}
```

Every verb can be a plain `Default*DataSource` (reflective defaults, same behavior as the
generated tier) or a hand-written class with entity-specific logic — mix freely per verb. Every
request still funnels through this one dynamically-registered resource at `/things`; nothing here
needs a second hand-written `@RestController` or base path.

## Other opt-in mechanics (any tier)

- **Optimistic concurrency**: `@jakarta.persistence.Version private Long version;` on the entity
  — a stale concurrent write gets `409` automatically. Single-item responses (`findOne`/
  `namedView`/`create`/`update`/`patch`) emit a strong `ETag` from it; `GET` honors
  `If-None-Match` (`304`); add `If-Match: <version>` (or a comma-list, or `*`) on a client request
  to a single-item `PUT`/`PATCH`/`DELETE`/write action for a `412` precondition check (strong
  comparison — a weak `W/"..."` validator never satisfies it).
- **Soft delete**: `implements SoftDeletable`; point `deleteDataSource` at
  `DefaultSoftDeleteDataSource` — flags a `deleted` column, automatically excluded from
  `findList`/`findPage*`/custom-read results. A write (`update`/`patch`/`deleteById`/a write
  action) on an already-flagged row 404s unconditionally. `findOne`/a named view still return it
  by default — set `restless.soft-delete.include-in-single-read=false` to 404 it there too.
- **Auditing**: `extends AbstractAuditableEntity` + `@EnableJpaAuditing` on the
  `@SpringBootApplication` class — populates `createdDate`/`lastModifiedDate`.
- **`@RestlessEmbed`** on a DTO field: `GET {basePath}/{id}?expand=name` populates it through
  *that* related resource's own `AuthorizationGuard` — an unreadable relation reads as
  empty/`null`, never `403`.
- **OpenAPI/Swagger**: add `springdoc-openapi-starter-webmvc-ui` to the classpath — every route
  this framework registers (generated or hand-wired, fixed or bulk or a named action) is
  documented at `/v3/api-docs`/`/swagger-ui.html` automatically, no extra config.
