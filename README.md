# spring-boot-restless

**Annotation-driven, DDD-friendly REST CRUD for Spring Boot — generate the boilerplate, hand-write the authorization.**

[![CI](https://github.com/cristivoicu/springboot_restless/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/cristivoicu/springboot_restless/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-0.0.1--SNAPSHOT-orange?logo=apachemaven&logoColor=white)](https://central.sonatype.com/)
[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

> **Status:** pre-1.0 (`0.0.1-SNAPSHOT`). Not yet published to Maven Central — build and install locally (see [Installation](#installation)). See [Versioning and API Stability](#versioning-and-api-stability) for what stability guarantees apply before `1.0`.
>
> **Looking for more depth?** This README is a concise reference. For a full tutorial-depth
> walkthrough of every mechanism — with sequence diagrams, the three-tier decision framework, and
> worked examples for each feature below — see [`docs/DEEP_DIVE.md`](docs/DEEP_DIVE.md).
>
> **Using [Claude Code](https://claude.com/claude-code)?** This repo ships a
> [`spring-boot-restless` Agent Skill](.claude/skills/spring-boot-restless/) that teaches it this
> framework's conventions (entity tiers, authorization/Cerbos wiring, write commands, bulk
> operations, the filter DSL). Copy `.claude/skills/spring-boot-restless/` into a consumer
> project's own `.claude/skills/` to get the same scaffolding help there.

---

## Table of contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [Quick Start](#quick-start)
- [Feature Guide](#feature-guide)
- [Advanced Usage](#advanced-usage)
- [Design Rationale](#design-rationale)
- [Roadmap](#roadmap)
- [Versioning and API Stability](#versioning-and-api-stability)
- [Contributing](#contributing)
- [Code of Conduct](#code-of-conduct)
- [Security Policy](#security-policy)
- [Changelog](#changelog)
- [License](#license)

## Overview

Every Spring Boot application with a relational domain model ends up writing the same shape of
code, over and over, per entity: a `@RestController`, a `JpaRepository`, a request/response
mapper, and the CRUD glue that wires them together. That boilerplate is not where an
application's real value lives — its value lives in the business rules that govern *who* can do
*what* to *which* row, and *which fields* they're allowed to see when they do.

**spring-boot-restless** removes the first kind of code and makes room for the second. Annotate
an entity, hand-write its four DTOs, and get a complete, versioned, paginated REST resource
registered at startup — no hand-subclassed controller, no repository interface, no mapper class.
What the framework deliberately does **not** generate is response shape (`Mapper<Entity, Dto>`)
or authorization (`AuthorizationGuard<E>`): those stay first-class, hand-written concerns, because
they're exactly the two seams where an application's actual rules have to live. An optional
[Cerbos](https://www.cerbos.dev/) integration backs that authorization seam with real
policy-as-code (YAML), evaluated against a real PDP, including row-level scoping and field-level
masking.

The result: for the majority of entities in a typical service — the ones with no special CRUD
logic — the only code written by hand is domain-meaningful. For the minority that need real
business rules, the framework steps out of the way cleanly instead of fighting you.

## Key Features

- **Zero-boilerplate, genuinely codeless CRUD** — an entity plus four DTOs (`CreateModel`,
  `UpdateModel`, `SearchDto`, `Dto`) is the entire surface area for a standard resource; a
  compile-time annotation processor generates the repository, a reflective mapper, and the
  resource glue class.
- **Three levels of control, chosen per entity, not globally** — fully generated, generated with
  one or two hand-written overrides (a custom `*DataSource`, a real guard), or a fully manual
  `RestlessResourceHandler` subclass for entities with genuinely custom logic.
- **Authorization as a framework-native, first-class seam** — `AuthorizationGuard<E>`'s three hook
  points (`preCheck` / `scope` / `canAccess`) sit on every route. No Spring Security dependency
  in the base library; plug in whatever principal source your own stack already populates.
- **Optional Cerbos-backed authorization** — policy-as-code row-level scoping (via Cerbos query
  plans translated into JPA `Specification`s) and field-level masking (`@CerbosHiddenField`)
  against a real PDP, fail-**closed** by design.
- **Bulk operations on every resource, unconditionally** — bulk create/update/delete, all
  transactional and fail-fast (every row guard-checked before anything is written).
- **Write commands for real domain transitions** — named, intent-carrying mutations
  (`POST {basePath}/{id}/actions/{name}`) for illegal-transition prevention and multi-step domain
  logic a full-replace `PUT` has no vocabulary to express.
- **A filter DSL with zero extra code** — `SearchDto` fields suffixed `Gte`/`Lte`/`Gt`/`Lt`/`Like`/
  `Ne`/`In` filter with that operator instead of plain equality, reflected automatically.
- **Fully synchronous, thread-safe by construction** — one stateless `RestlessResourceHandler`
  Spring bean per resource, no mutable shared state; every request-scoped value is a method
  parameter or a local, never a field.
- **RFC 9457-compliant error responses**, automatic OpenAPI/Swagger documentation, optimistic
  concurrency (`@Version` + `If-Match`), soft delete, and JPA auditing — all opt-in, all with
  minimal or zero extra code.

## Requirements

| Component | Version |
|---|---|
| Java | 25+ |
| Spring Boot | 4.1.x |
| Build tool | Maven 3.9+ (a wrapper, `./mvnw`, is bundled) |
| Persistence | Spring Data JPA (a relational database with a `Specification`-capable `JpaRepository`) |
| Authorization backend (optional) | [Cerbos](https://www.cerbos.dev/) PDP, via `spring-boot-restless-cerbos` |

No other infrastructure is required for the base library — the optional `cerbos` module is the
only piece with an external runtime dependency (a running Cerbos PDP).

## Installation

The project isn't published to Maven Central yet (still `0.0.1-SNAPSHOT` — see the status note
above). Build and install it into your local repository first:

```bash
git clone https://github.com/cristivoicu/springboot_restless.git
cd springboot_restless
./mvnw install
```

Then declare the dependency exactly as you would any other library.

### Maven

```xml
<dependency>
    <groupId>ro.cristivoicu</groupId>
    <artifactId>spring-boot-restless</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>

<!-- Optional: compile-time codegen from @RestlessEntity -->
<dependency>
    <groupId>ro.cristivoicu</groupId>
    <artifactId>spring-boot-restless-processor</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>

<!-- Optional: Cerbos-backed AuthorizationGuard + field masking -->
<dependency>
    <groupId>ro.cristivoicu</groupId>
    <artifactId>spring-boot-restless-cerbos</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Or import the BOM once and drop the version from every `spring-boot-restless*` dependency:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>ro.cristivoicu</groupId>
            <artifactId>spring-boot-restless-dependencies</artifactId>
            <version>0.0.1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("ro.cristivoicu:spring-boot-restless:0.0.1-SNAPSHOT")
    annotationProcessor("ro.cristivoicu:spring-boot-restless-processor:0.0.1-SNAPSHOT") // optional

    implementation("ro.cristivoicu:spring-boot-restless-cerbos:0.0.1-SNAPSHOT") // optional
    testImplementation("ro.cristivoicu:spring-boot-restless-test:0.0.1-SNAPSHOT") // optional
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'ro.cristivoicu:spring-boot-restless:0.0.1-SNAPSHOT'
    annotationProcessor 'ro.cristivoicu:spring-boot-restless-processor:0.0.1-SNAPSHOT' // optional

    implementation 'ro.cristivoicu:spring-boot-restless-cerbos:0.0.1-SNAPSHOT' // optional
    testImplementation 'ro.cristivoicu:spring-boot-restless-test:0.0.1-SNAPSHOT' // optional
}
```

No `@ComponentScan`/`@Import` needed beyond that — every framework infrastructure bean
(`RestlessRegistrar`, exception handling, OpenAPI customization, the Cerbos client) is registered
automatically via Spring Boot's own auto-configuration mechanism the moment the jar is on the
classpath.

## Configuration

All properties are optional; every one below shows its default. None are required to get a
working resource.

```yaml
restless:
  list:
    # Hard cap on GET {basePath}/list (unpaginated read). A response that hits the cap carries
    # the response header X-Restless-List-Truncated: true.
    max-size: 10000

# Only read if spring-boot-restless-cerbos is on the classpath.
cerbos:
  client:
    # host:port of a running Cerbos PDP.
    target: localhost:3593
    # Use a plaintext (non-TLS) gRPC channel - set to false against a PDP with TLS enabled.
    plaintext: true
    # gRPC deadline applied to every check()/plan() call. A PDP that doesn't answer within this
    # window is treated as unreachable - the guard fails CLOSED (denies), never open.
    timeout: 3s
```

| Property | Type | Default | Description |
|---|---|---|---|
| `restless.list.max-size` | `int` | `10000` | Maximum rows returned by the unpaginated `GET {basePath}/list` route. |
| `restless.page.max-size` | `int` | `2000` | Maximum client-supplied `size` on `GET {basePath}`/`.../overview`/`.../select/async`/a named read action - a request over this is rejected `400`, not silently clamped. |
| `restless.bulk.max-size` | `int` | `1000` | Maximum items in a single `createBulk`/`updateBulk`/`deleteAll` request - over this is rejected `400` before any item is processed. |
| `cerbos.client.target` | `String` | `localhost:3593` | Cerbos PDP address (`host:port`), gRPC. |
| `cerbos.client.plaintext` | `boolean` | `true` | Whether the gRPC channel to the PDP is unencrypted. |
| `cerbos.client.timeout` | `Duration` | `3s` | Deadline for a single `check`/`plan` RPC before the guard fails closed. |

No properties are needed to enable/disable a route, a bulk operation, or a write command — those
are declared per resource in Java (`@RestlessEntity`'s attributes, or overriding a
`RestlessResourceHandler` method), not via `application.yml`. See the
[customization cheat sheet](#advanced-usage) below for where each of those lives.

## Quick Start

The core usage pattern is not "inject a bean and call a method" — it's "declare an entity and its
DTOs, and the framework registers a live Spring MVC resource for you." This is the library's
defining feature: for a standard entity, no service, controller, or repository class is written
by hand at all.

**1. Annotate the entity:**

```java
import jakarta.persistence.*;
import lombok.*;
import ro.cristivoicu.springbootrestless.annotation.RestlessEntity;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@RestlessEntity(basePath = "/tasks", allowAll = true) // allowAll=true: no guard yet, see Advanced Usage
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private boolean done;
}
```

**2. Write its four DTOs, in the same package:**

```java
public class TaskCreateModel implements CreateModel {
    @NotBlank private String title;
    private boolean done;
    // + Lombok @Getter @Setter
}

public class TaskUpdateModel implements UpdateModel {
    @NotBlank private String title;
    private boolean done;
}

public class TaskSearchDto extends AbstractSearchDto {
    private String title; // equality filter, zero extra code
}

public class TaskDto implements EntityDto {
    private Long id;
    private String title;
    private boolean done;
}
```

**3. Build.** `RestlessEntityProcessor` (the `spring-boot-restless-processor` annotation
processor) generates `TaskRepository`, a reflective `TaskMapper`, and `TaskRestlessResource` from
just the code above. At application startup, `RestlessRegistrar` finds the generated resource and
registers every route live against a real Spring Data JPA repository:

```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

That's it — no `@RestController`, no `@Service`, no `TaskRepository` written by hand. The
following endpoints are live immediately:

| Verb | Route | Behavior |
|---|---|---|
| `POST` | `/tasks` | Create one (`201 Created` + `Location`) |
| `POST` | `/tasks/bulk` | Bulk create (all-or-nothing) |
| `GET` | `/tasks/{id}` | Read one |
| `GET` | `/tasks` | Paged read |
| `GET` | `/tasks/list` | Unpaginated read (capped by `restless.list.max-size`) |
| `PUT` | `/tasks/{id}` | Full replace |
| `PUT` | `/tasks/bulk` | Bulk update, keyed by id |
| `DELETE` | `/tasks/{id}` | Delete one |
| `POST` | `/tasks/bulk-delete` | Bulk delete |
| `GET` | `/tasks?titleLike=foo&sort=title,asc` | Filter DSL + multi-field sort |

## Feature Guide

Everything below works on any resource, any tier (codeless, generated-with-overrides, or fully
manual), unless a subsection says otherwise. See [`docs/DEEP_DIVE.md`](docs/DEEP_DIVE.md) for the
full tutorial version of each — diagrams, request-flow walkthroughs, and worked, runnable
examples from the `example` module.

### Write commands

CRUD's fixed verbs (`create`/`update`/`patch`/`delete`) are shaped around *fields*, not
*transitions* — a full-replace `PUT` can set a field to any value at all, including an illegal
one, because "replace these fields" has no vocabulary for "this is only a legal move from certain
prior states." A **write command** is a named, intent-carrying mutation where the server, not the
client, decides what's legal:

```
POST {basePath}/{id}/actions/{name}
```

Declared per resource via `getCustomWriteActions()` (hand-wired tier only today — see the cheat
sheet below):

```java
public interface WriteAction<E, Req extends WriteActionRequest, Resp> {
    Class<Req> getRequestType();
    Class<Resp> getResponseType();

    // Runs against an already-loaded, already-guard-checked entity and a validated request body,
    // inside one real transaction.
    Resp execute(E entity, Req request) throws Exception;
}
```

```java
@Override
public Map<String, WriteAction<Employee, ?, ?>> getCustomWriteActions() {
    return Map.of(
            "promote", new WriteAction<Employee, PromoteRequest, EmployeeDto>() {
                @Override public Class<PromoteRequest> getRequestType() { return PromoteRequest.class; }
                @Override public Class<EmployeeDto> getResponseType() { return EmployeeDto.class; }

                @Override
                public EmployeeDto execute(Employee entity, PromoteRequest request) {
                    JobTitle newTitle = request.getNewJobTitle();
                    if (!newTitle.isPromotionFrom(entity.getJobTitle())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "'" + newTitle + "' is not a promotion from '" + entity.getJobTitle() + "'");
                    }
                    entity.setJobTitle(newTitle);
                    return mapper.map(repository.save(entity));
                }
            }
    );
}
```

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"newJobTitle":"ENGINEER"}' http://localhost:8081/employees/1/actions/promote
```

What happens under the hood, per request: `preCheck(WRITE_ACTION, "promote")` → read + validate
the request body → open a transaction → load the entity → `canAccess(WRITE_ACTION, "promote",
entity)` → `execute(...)` → commit → `200 OK`. A guard denial or a `ResponseStatusException`
thrown from `execute` (illegal-transition prevention needs nothing else) rolls the whole
transaction back. `execute` bypasses `Mapper` entirely, both directions — its request/response
shape is whatever the action author decides, the same "response shape is always hand-written"
boundary `Mapper` itself states.

**Use it for:** a transition with real invariants (`promote`'s career-ladder check), a
business-rule cap that depends on the row's *current* state (`giveRaise`'s 20%-per-raise cap), or
append-only mutation of a collection the update model deliberately never exposes
(`addCertification`). **Don't use it for:** a plain field-level replace (that's what `PUT`/`PATCH`
already are) or bulk mutation (no `BulkWriteAction` exists yet — one call is one entity).

### Bulk operations

Every resource gets three bulk routes for free — no opt-in, riding on the same
`CreateDataSource`/`UpdateDataSource`/`DeleteDataSource` every resource already has:

| Verb | Route | Body | Response |
|---|---|---|---|
| `POST` | `{basePath}/bulk` | JSON array of `CreateModel` | `200`, array of created DTOs |
| `PUT` | `{basePath}/bulk` | JSON object keyed by id — `{"1": {...}, "2": {...}}` | `200`, array of updated DTOs |
| `POST` | `{basePath}/bulk-delete` | `{"ids": ["1", "2", "3"]}` | `204 No Content` |

Every bulk write is one real transaction — all-or-nothing, not best-effort. Bulk update/delete
additionally load and `canAccess`-check every target **before** writing anything, so a batch never
partially applies because item #7 of 10 turned out to be denied.

```bash
curl -X PUT -H "Content-Type: application/json" \
  -d '{"1": {"title":"Ship it","done":true}, "2": {"title":"Write docs","done":false}}' \
  http://localhost:8081/tasks/bulk
```

### Filter DSL

A `SearchDto` field suffixed with a recognized operator filters with that operator instead of
plain equality — zero extra code, reflected over automatically:

| Suffix | Operator | Example |
|---|---|---|
| `Gte` / `Lte` / `Gt` / `Lt` | `>=` / `<=` / `>` / `<` | `?salaryGte=100000` |
| `Like` | contains-match | `?titleLike=urgent` |
| `Ne` | not-equal | `?statusNe=CLOSED` |
| `In` | membership (repeatable param) | `?statusIn=OPEN&statusIn=PENDING` |

Combine with multi-field sort, Spring Data's own convention: `?sort=lastName,asc&sort=firstName,asc`.
For anything beyond these seven operators (joins, boolean OR, cross-field logic), override
`getSpecification()` — `RestlessSpecifications` is a small fluent builder for that escape hatch.

### Partial updates (`PATCH`)

Entirely opt-in, unlike the four mandatory CUD verbs — no `PATCH` route is registered until one is
provided:

```java
@RestlessEntity(basePath = "/tasks", patchDataSource = DefaultPatchDataSource.class)
```

`null` on a `PatchModel` field means "not sent," not "clear it" — a full `PUT` is still how a
client explicitly nulls a field. Checked against its own `Action.PATCH`, not inherited from
`Action.UPDATE`, so granting full-replace access never silently grants partial-update access too.

### Soft delete

```java
public class Task implements SoftDeletable { /* ... */ }
```

```java
@RestlessEntity(basePath = "/tasks", deleteDataSource = DefaultSoftDeleteDataSource.class)
```

Flags a `deleted` column instead of removing the row; automatically excluded from
`findList`/`findPage*`/custom-read results, but still fetchable directly by id.

### Optimistic concurrency

```java
@Version
private Long version;
```

That's the entire change — a stale concurrent write already gets a `409` automatically. Add an
`If-Match: <version>` header to a single-item `PUT`/`PATCH`/`DELETE` to opt further into a
precondition check (`412` on a stale value, checked before any write is attempted).

### Auditing

```java
public class Task extends AbstractAuditableEntity { /* ... */ }
```

Populates `createdDate`/`lastModifiedDate` automatically via Spring Data JPA's own auditing —
add `@EnableJpaAuditing` on your `@SpringBootApplication` class, same as any Spring Data JPA app.

### API versioning

```java
@RestlessEntity(basePath = "/tasks", version = "1")
```

Forwards onto Spring Framework's own `RequestMappingInfo.Builder.version(...)` for every route
this resource registers — real routing, not just a stored string. *How* a request's version is
resolved (header, path segment, query param) is a separate, app-wide `ApiVersionConfigurer` bean.

### Related-resource embedding

```java
public class TaskDto implements EntityDto {
    @RestlessEmbed(resource = ProjectRestlessResource.class, sourceField = "projectId", targetField = "id")
    private ProjectDto project;
}
```

`GET /tasks/{id}?expand=project` populates the field through *that* resource's own
`AuthorizationGuard` — an unreadable relation reads as empty/`null`, never a `403`, so it never
fails the outer response.

### OpenAPI / Swagger documentation

Add `springdoc-openapi-starter-webmvc-ui` to the classpath and every route this framework
registers — generated or hand-wired, fixed or bulk or a named action — is documented at
`/v3/api-docs`/`/swagger-ui.html` automatically, with real request/response schemas. No extra
configuration needed.

### Observability

With `spring-boot-starter-actuator` on the classpath, every `AuthorizationGuard` denial
increments a `restless.authorization.denials` Micrometer counter (tagged `resource`/`action`/
`hook`); the optional `cerbos` module also registers a `CerbosHealthIndicator` reporting whether
the PDP is reachable, surfaced on `/actuator/health`.

## Advanced Usage

### Wiring authorization into the core component

The one thing every resource should add before going to production is a real
`AuthorizationGuard<E>` — the framework refuses to start a resource with none configured unless
`allowAll = true` explicitly says otherwise. This is the "inject and use a core component in a
Spring service" pattern for this library: a hand-written `@Component` implementing the guard
interface, injected wherever your own auth stack's principal source lives.

```java
@Component
public class TaskAuthorizationGuard implements AuthorizationGuard<Task> {

    @Override
    public boolean preCheck(Action action, String customActionName, HttpServletRequest request) {
        return request.isUserInRole("TASK_USER");
    }

    @Override
    public boolean canAccess(Action action, HttpServletRequest request, Task entity) {
        String owner = request.getUserPrincipal().getName();
        return owner.equals(entity.getOwnerUsername());
    }
}
```

```java
@RestlessEntity(basePath = "/tasks", authorizationGuard = TaskAuthorizationGuard.class)
public class Task { /* ... */ }
```

### Overriding a single verb's data source

Entity-specific create/update/delete logic doesn't require abandoning the generated tier — point
one `@RestlessEntity` attribute at a hand-written class instead of falling back to the
naming-convention default:

```java
@Component
public class TaskCreateDataSource extends CreateDataSource<Task, Long, TaskCreateModel> {

    public TaskCreateDataSource(TaskRepository repository) {
        super(repository);
    }

    @Override
    public Task create(TaskCreateModel model) {
        Task task = new Task();
        task.setTitle(model.getTitle().strip());
        task.setDone(false); // a new task is never created already done
        return repository.save(task);
    }
}
```

```java
@RestlessEntity(basePath = "/tasks", createDataSource = TaskCreateDataSource.class,
        authorizationGuard = TaskAuthorizationGuard.class)
public class Task { /* ... */ }
```

### Backing the guard with Cerbos policy-as-code

```java
@Override
protected AuthorizationGuard<Task> getAuthorizationGuard() {
    return new CerbosAuthorizationGuard<>(cerbosClient, "task", Task::getId,
            task -> Map.of("ownerUsername", AttributeValue.stringValue(task.getOwnerUsername())));
}
```

### Customization cheat sheet

| I want to... | Do this | Tier |
|---|---|---|
| Add a new entity with default CRUD | Entity + four DTOs, `@RestlessEntity(basePath = "...")` | Codeless |
| Add entity-specific CUD logic | Point `createDataSource`/`updateDataSource`/`deleteDataSource` at a hand-written class | Generated + override |
| Control who can do what | `authorizationGuard = ...` / override `getAuthorizationGuard()` | Any |
| Back authorization with policy-as-code | `CerbosAuthorizationGuard<E>` (`spring-boot-restless-cerbos`) | Any |
| Hide individual fields per caller | `@CerbosHiddenField` + `CerbosFieldMasker`, called from your `Mapper` | Any (needs `cerbos`) |
| Restrict which routes exist | `operations = {...}` / override `getEnabledOperations()` | Any |
| Add partial update (`PATCH`) | `patchDataSource = ...` / override `getPatchDataSource()` | Any |
| Filter beyond plain equality | Suffix a `SearchDto` field (`Gte`/`Lte`/`Gt`/`Lt`/`Like`/`Ne`/`In`) | Any |
| Add a filtered read beyond `SearchDto` | `getCustomReadActions()` | Hand-wired only |
| Add a domain-meaningful mutation ("promote", "cancel") | `getCustomWriteActions()` — see [Write commands](#feature-guide) | Hand-wired only |
| Expose a different response shape of the same entity | `getNamedViews()` | Hand-wired only |
| Create/update/delete many rows in one request | `POST`/`PUT {basePath}/bulk`, `POST {basePath}/bulk-delete` | Any, unconditional |
| Add optimistic concurrency | `@jakarta.persistence.Version` on the entity | Any |
| Soft-delete instead of hard-delete | Implement `SoftDeletable`, `deleteDataSource = DefaultSoftDeleteDataSource` | Any |
| Add `createdDate`/`lastModifiedDate` | Extend `AbstractAuditableEntity` + `@EnableJpaAuditing` | Any |
| Version an API | `version = "..."` on `@RestlessEntity`/`@RestlessResource` | Any |
| Expand a related resource inline | `@RestlessEmbed` on a DTO field, `?expand=name` | Any |
| Cap an unbounded `GET .../list` | `restless.list.max-size` (default `10000`) | Global |
| Cap a client-supplied page `size` | `restless.page.max-size` (default `2000`) - rejects `400` over the cap | Global |
| Cap a bulk create/update/delete request | `restless.bulk.max-size` (default `1000`) - rejects `400` over the cap | Global |

For the full, worked, runnable version of every capability above — including a real Cerbos +
Keycloak authorization demo — see the [`example`](example/README.md) module and
[`docs/DEEP_DIVE.md`](docs/DEEP_DIVE.md).

## Design Rationale

Why three specific mechanisms above are shaped the way they are — the alternatives considered and
rejected, not just the shape that shipped.

### Filter DSL: operator suffixes, not a query language

The gap: `getSpecification()` only ever ANDed together `cb.equal(...)` for non-null `SearchDto`
fields — no ranges, no `LIKE`, no `IN`. Three designs were considered:

- **A. An RSQL/FIQL query string** (`?filter=age=ge=30;name==Jo*`, GitHub/Atlassian-style). One
  query param, arbitrarily nested boolean logic — but a real grammar to parse *and secure*: every
  operator/property combination needs validating against the entity's actual fields and types
  before it reaches a `CriteriaBuilder` call, or a client can probe for fields the `SearchDto`
  never declared. Heavier to implement and document than the common case justifies.
- **B. Structured operator-suffix query params** (`ageGte`/`nameLike`/`statusIn`, what shipped).
  Extends the existing "reflect over `SearchDto`'s declared fields" mechanism instead of replacing
  it — the existing whitelist-by-declaration security property is preserved for free, every query
  param is self-documenting in the generated OpenAPI schema, and it's the same reflection loop
  `getSpecification` already had, with a switch on suffix instead of always `equal`. No boolean
  `OR`/arbitrary nesting, but that covers the large majority of real filter needs (ranges +
  partial match + membership) — the same 80/20 argument the framework already makes for
  equality-match defaults.
- **C. `RestlessSpecifications`, a fluent builder for the hand-written escape hatch itself.** Not a
  query-string DSL at all — shipped alongside B, since it's nearly free and makes every
  hand-written `getSpecification()` override or custom read action shorter to write.

**Shipped: B and C together; A only if a real consumer asks for boolean OR/nesting** — a strictly
bigger, riskier surface for a need B doesn't cover. See `GizmoFilterTest`/`GizmoSearchDto` for the
proof, and `GadgetRestlessResource`'s `getSpecification()`/`byEmailDomain` action for `C` used as a
drop-in replacement for a hand-written lambda.

### Write commands: mirror `ReadAction`, don't reinvent

The gap this closed: a named-action escape hatch already existed for **reads**
(`ReadAction`/`getCustomReadActions()`), but none for **writes** — every mutation was CRUD-shaped.
There was no way to express `POST /employees/{id}/promote` without a hand-written
`@RestController` outside the framework entirely, forfeiting routing/error-contract/OpenAPI parity
with everything else. The DDD argument: an anemic model + CRUD is fine for a genuinely simple
service, and becomes an anti-pattern the moment a bounded context has real, ever-changing business
rules — a client sending `{"status": "PROMOTED"}` via `update()` can express any transition,
including illegal ones, because full-replace `PUT` has no vocabulary for "this is only valid from
these prior states."

`WriteAction<E, Req, Resp>` deliberately mirrors `ReadAction`'s shape (same "one named extra route,
one shared dispatch method" idiom) so an entity author who's already used
`getCustomReadActions()` needs to learn nothing new — see
[Tutorial: write commands](#feature-guide) above for the shipped shape.

**Bulk vs. single-item**, decided but not yet built: a bulk command (`POST
/employees/actions/give-raise` with a list of ids + a percentage) is a legitimate second shape,
structurally closer to `deleteAll`'s fail-fast-before-mutating pattern than to the single-entity
`WriteAction`. Recommended sequencing was single-entity first (covers the large majority of real
"commands"), with `BulkWriteAction<E, Req, Resp>` as a follow-up reusing `inTransaction`'s
all-or-nothing wrapping — see [Roadmap](#roadmap).

**What this doesn't try to solve**: domain events (a command's natural companion is "and then
publish `EmployeePromoted`" — the dispatch point right after `WriteAction#execute` returns
successfully, still inside the transaction, is exactly where an `ApplicationEventPublisher`/
transactional-outbox write would go, worth designing for even before it's built) and compile-time
(`@RestlessEntity`) support (prove the shape hand-wired first, same path custom read actions
themselves took).

### Keyset (cursor) pagination — proposed, not implemented

`findPage`/`findPageOverview`/`findPageSelect` all use offset pagination today (`LIMIT size OFFSET
page*size`), which has two well-known problems at this framework's own target scale (a table past
a few hundred thousand rows): deep pages get slow (`OFFSET 100000` still walks and discards 100,000
rows — `O(offset)` per request, not `O(size)`), and pages shift under concurrent writes (a row
deleted between two page fetches can make the client skip or double-see a row).

Keyset pagination fixes both: the client sends "give me the next page *after* this specific row,"
encoded as that row's own sort-key values, and the database does an indexed range scan (`WHERE
(sortkey) > (lastSeenValue)`) — `O(size)` regardless of depth, stable under concurrent
inserts/deletes before the cursor position. This framework is unusually well positioned for it:
`AbstractSearchDto#getPageable()` already defaults to `Sort.by(ASC, "id")` when no `sort` param is
sent, so **every existing route already sorts by a unique column by default** — keyset pagination
*requires* a unique tie-breaker (otherwise "the row after this one" is ambiguous when several rows
share sort-key values), which most frameworks retrofitting this have to bolt on and this one
already has, just needs to become *required* rather than *incidental* in cursor mode.

Recommended shape, if/when built: a new `CursorPageableResponse<B>` envelope (`body` +
`nextCursor` + `hasMore` — deliberately **no** `totalElements`/`totalPages`, since computing those
needs the same expensive `COUNT(*)`/full scan cursor pagination exists to avoid), opted into via a
`cursor=` query param on the *existing* `/page` routes rather than a new route (no cursor param ->
today's offset behavior, unchanged; a `cursor` param present -> keyset mode — same
`AuthorizationGuard.Action.READ_PAGE`, same filter/scope composition, cursor mode only changes how
the `Pageable`-equivalent is built and how the result is packaged). The actual seek predicate for a
multi-column sort is a lexicographic "greater than" over a tuple (`lastName > :lastName OR
(lastName = :lastName AND id > :id)`), a genuinely reusable, entity-agnostic ~40-line utility. The
real trade-off worth knowing up front: no "jump to page 47" UX — keyset pagination is inherently
sequential (next/previous only), the right fit for infinite-scroll/API-to-API consumption, not a
UI with numbered page links, which is exactly why it's designed as additive (`cursor=`) rather than
a replacement for offset paging.

## Roadmap

Tier 0 (adoptability) and Tier 1 (standards correctness) are done — see [Changelog](#changelog).
This tracks what's next, roughly in recommended build order. Nothing here is scheduled; it's a
priority-ordered backlog with design notes, not a commitment.

**Next up:**

1. **[Write commands](#write-commands)** — ✅ done (hand-wired tier).
   `@RestlessEntity`/annotation-processor support remains open — see
   [Design Rationale](#design-rationale)'s "what this doesn't try to solve" note.
2. **[Filter DSL](#filter-dsl)** — ✅ done. Operator-suffix convention over the existing
   `SearchDto` reflection loop, plus `RestlessSpecifications` — see
   [Design Rationale](#design-rationale) for why this shape won over an RSQL query string.
3. **[Keyset pagination](#design-rationale)** — offset pagination degrades at depth and shifts
   under concurrent writes; adds an opt-in `cursor=` mode alongside (not replacing) today's
   `page=`/`size=`. Independent of the above two; safe to build in parallel.

**Also worth doing, smaller:**

- **Idempotency keys** on `POST` (`Idempotency-Key` header, request-fingerprint-keyed dedupe
  table) — pairs naturally with write commands, since a non-idempotent command is exactly where a
  client most wants a safe-retry story.
- **Domain events / transactional outbox** — the natural companion to write commands (see
  [Design Rationale](#design-rationale)'s "what this doesn't try to solve"). Design the publish
  hook when write commands ship even if the actual publisher wiring comes later.
- **GraalVM native-image support** — `RestlessRegistrar`'s runtime `registerMapping` and every
  reflection-based default (`Default*DataSource`, the generated `Mapper`, `getSpecification`) are
  invisible to native-image's static analysis without hand-written `RuntimeHints`. Real but
  bounded work; low priority unless a consumer actually asks for it.

**Deliberately not scheduled** (would need a decision only the maintainer can make):

- **Maven Central publishing** — needs Sonatype/GPG credentials.
- **Kotlin support, GraphQL adapter, WebFlux port, OPA/Cedar guard backends, a conformance TCK** —
  each is a multi-week effort that would roughly double the surface area this project has to
  maintain. Worth reconsidering if and when a concrete consumer asks for one specifically, not
  speculatively.

## Versioning and API Stability

`spring-boot-restless` is pre-1.0 (every module currently ships as `0.0.1-SNAPSHOT`). This section
says, honestly, what that means for anyone depending on it today: what's stable enough to build
on, what's still free to change shape, and what to expect once 1.0 actually ships.

**Before 1.0:** no compatibility guarantee exists yet across any release. A `0.0.1-SNAPSHOT` build
today may not be source- or binary-compatible with the next one. That said, churn is concentrated
in specific places (below) — most of the framework's public surface has been stable for a while in
practice, just not yet under a stated promise.

**What's intended to be the stable surface once 1.0 ships** — the extension points an entity
author or a consuming application actually writes code against, where compatibility will be
prioritized first:

- `RestlessResourceHandler<E, K>` and its `get*DataSource()`/`getAuthorizationGuard()`/
  `getSpecification()`/`getCustomReadActions()`/`getEnabledOperations()` extension points.
- `AuthorizationGuard<E>` and its three hook points (`preCheck`/`scope`/`canAccess`).
- The `@Restless*` annotation set: `@RestlessEntity`, `@RestlessResource`, `@RestlessEmbed`,
  `@RestlessMapperExclude`, `@CerbosHiddenField`.
- The `*DataSource` base classes (`CreateDataSource`, `ReadDataSource`, `UpdateDataSource`,
  `DeleteDataSource`, `PatchDataSource`) and their `Default*DataSource` implementations.
- `Mapper<E, D>`, `SearchDto`/`AbstractSearchDto`, `SoftDeletable`, `AbstractAuditableEntity`.
- `CerbosAuthorizationGuard<E>`, `CerbosResourceAttributesMapper<E>`, `CerbosFieldMasker`.

**What's internal, and still expected to change shape:**

- `RestlessRegistrar`'s internals (route-registration mechanics, constructor parameter order).
- `ResourceMetadata`'s exact field list and constructor arity — already grown twice (per-projection
  response types, API version) and may again.
- `RestlessOpenApiCustomizer`'s internals (a best-effort documentation generator, not a contract
  any code should depend on beyond "produces a valid OpenAPI document").
- Anything under a `fixtures`/test-only package in any module.

**After 1.0:** standard semver. A breaking change to anything in the stable-surface list above
ships only in a new major version, with a changelog entry naming exactly what broke and why. A
minor version may add new optional attributes/methods (with defaults, so existing implementations
keep compiling) but never removes or repurposes an existing one. A patch version is bug fixes only.

## Contributing

Thanks for considering a contribution. This is a small, opinionated framework with a lot of design
reasoning baked into its javadoc — reading a class's javadoc before changing it will usually save
you a round-trip.

**Before you start:** for anything beyond a small fix (a typo, an obvious bug), please open an
issue first describing what you want to change and why. This project has explicit, documented
design boundaries (see [Overview](#overview) above, [`docs/DEEP_DIVE.md`'s Scope section](docs/DEEP_DIVE.md#scope),
and [Versioning and API Stability](#versioning-and-api-stability)) — a PR that crosses one of them
without prior discussion is likely to be declined even if the code itself is good.

**Development setup:**

- **Java 25** and **Maven 3.9+** (`./mvnw` is included, no local Maven install required).
- **Docker** and **Docker Compose**, only for the `cerbos` module's Testcontainers-backed tests and
  `example`'s Cerbos-backed test suite (every `EmployeeAuthorizationGuardTest`-style test starts a
  real Cerbos PDP container). Everything else runs against plain H2, no Docker needed.

```bash
./mvnw test              # whole reactor
./mvnw -pl app test       # one module
```

**Using Claude Code on this repo:** [`.claude/skills/spring-boot-restless/`](.claude/skills/spring-boot-restless/)
is an Agent Skill that teaches Claude Code this framework's conventions — the three-tier decision
framework for adding an entity, authorization guard/Cerbos wiring, write commands, bulk operations,
and the filter DSL. It loads automatically when relevant while working in this repo. Copy the whole
directory into a consumer project's own `.claude/skills/` to get the same assistance there.

**Module map:** see [`docs/DEEP_DIVE.md`'s Modules section](docs/DEEP_DIVE.md#modules) for the
full picture — in short, `processor` (compile-time codegen), `app` (the framework), `cerbos`
(optional Cerbos-backed auth), `example` (a realistic consumer app, also this project's end-to-end
test bed).

**Code style:**

- Match the surrounding code's comment density and idiom — this codebase explains *why*, not just
  *what*, in javadoc; a new class with none at all reads as unfinished here.
- No new hand-written boilerplate where a `Default*` class or the annotation processor could
  generate it — if you're writing something every entity would need, it probably belongs in
  `app`, not in `example`.
- Every behavior change needs a test. `app`'s own fixtures (`Gadget`/`Gizmo`/`Sprocket`/
  `Doohickey`/...) exist so the framework can be tested in isolation, without `example` — prefer
  adding to those unless the change is specifically about realistic, business-named usage.

**Commit messages / PRs:**

- Keep commits focused; explain *why* in the body when the change isn't self-evident from the
  diff.
- Run `./mvnw test` for the whole reactor before opening a PR (or note which modules you couldn't
  run, e.g. "no Docker available locally").
- Update the relevant module's tests and, if user-facing, this README/`example/README.md` in the
  same PR — a behavior change without a doc update is treated as incomplete.

**Reporting bugs vs. security issues:** security vulnerabilities go through
[Security Policy](#security-policy) below, not a public issue.

## Code of Conduct

Adapted from the [Contributor Covenant](https://www.contributor-covenant.org), version 2.1.

**Our Pledge:** we as members, contributors, and leaders pledge to make participation in our
community a harassment-free experience for everyone, regardless of age, body size, visible or
invisible disability, ethnicity, sex characteristics, gender identity and expression, level of
experience, education, socio-economic status, nationality, personal appearance, race, religion, or
sexual identity and orientation.

**Our Standards** — examples of behavior that contributes to a positive environment: demonstrating
empathy and kindness toward other people; being respectful of differing opinions, viewpoints, and
experiences; giving and gracefully accepting constructive feedback; accepting responsibility and
apologizing for mistakes, and learning from them.

Examples of unacceptable behavior: the use of sexualized language or imagery, and sexual attention
of any kind; trolling, insulting or derogatory comments, and personal or political attacks; public
or private harassment; publishing others' private information without explicit permission.

**Enforcement Responsibilities:** project maintainers are responsible for clarifying and enforcing
standards of acceptable behavior and will take appropriate and fair corrective action in response
to any behavior deemed inappropriate, threatening, offensive, or harmful.

**Scope:** this Code of Conduct applies within all community spaces (issues, pull requests,
discussions) and when an individual is officially representing the project in public spaces.

**Enforcement:** instances of abusive, harassing, or otherwise unacceptable behavior may be
reported to the project maintainer via a GitHub issue marked confidential. All complaints will be
reviewed and investigated promptly and fairly.

## Security Policy

**Supported Versions:** `spring-boot-restless` is pre-1.0 (`0.0.1-SNAPSHOT`) and under active
development — only the latest commit on `main` is supported. There is no backport policy yet; see
[Versioning and API Stability](#versioning-and-api-stability) above for what "pre-1.0" means for
API stability.

**Reporting a Vulnerability:** please **do not** open a public GitHub issue for a security
vulnerability. Instead, report it privately via GitHub's private vulnerability reporting (Security
tab -> "Report a vulnerability" on this repository). Include:

- A description of the vulnerability and its impact.
- Steps to reproduce (a minimal `@RestlessResource`/`@RestlessEntity` repro is ideal, given how
  much of this framework's surface is annotation-driven).
- Which module (`app`, `cerbos`, `processor`, ...) and version/commit are affected.

You should receive an acknowledgement within a few days. This is a single-maintainer project run
outside of paid time, so response time may vary — please be patient, and thank you for reporting
responsibly.

**Scope** — things that count as a security issue here:

- Authorization bypass in `AuthorizationGuard`'s three hook points (`preCheck`/`scope`/
  `canAccess`), `RestlessRegistrar`'s startup fail-fast-on-no-guard check, or the Cerbos-backed
  guard/field-masker in the `cerbos` module.
- Mass-assignment / unintended field exposure through the reflective `Default*DataSource` classes
  or the reflective default `Mapper` the annotation processor can generate.
- Anything that lets one `@RestlessResource` read/write another's data (cross-resource isolation).
- Injection via the reflection-driven default search filter (`getSpecification`) or
  `@RestlessEmbed` resolution.

Things that are **out of scope** (report as an ordinary bug/issue instead): denial-of-service via
an intentionally pathological request shape against a demo/example app (`example` module) not
meant for production use as-is; findings that require modifying policy YAML, application
properties, or Java code you control to reach.

## Changelog

All notable changes to this project are documented here. The format is loosely based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project doesn't follow SemVer yet
(still `0.0.1-SNAPSHOT`, pre-1.0) — see [Versioning and API Stability](#versioning-and-api-stability)
above for what stability guarantees actually apply before 1.0.

### [Unreleased]

**Added**

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
  unchanged). See [Design Rationale](#design-rationale).
- **Write commands.** `WriteAction<E, Req, Resp>` / `getCustomWriteActions()` (`app`), mirroring
  the existing `ReadAction`/`getCustomReadActions()` mechanism: a named, intent-carrying mutation
  (`POST {basePath}/{id}/actions/{name}`) beyond the fixed create/update/patch/delete verbs, for a
  transition a full-replace `PUT` has no vocabulary to guard (illegal-state prevention,
  multi-step domain logic). Runs inside a real transaction (load + guard-check + `execute`),
  documented automatically by `RestlessOpenApiCustomizer`. New
  `AuthorizationGuard.Action.WRITE_ACTION` enum value (additive), and `CerbosActionNaming` now
  forwards a write action's own name to the policy (`cerbos` module) the same way it already did
  for named read actions/views. Hand-wired tier only for now — see [Design Rationale](#design-rationale)
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

**Fixed**

- `SECURITY.md`/[Security Policy](#security-policy) pointed at a `<developers>` section in the
  root `pom.xml` that never existed - dead end for anyone trying to report a vulnerability
  privately. Now relies solely on GitHub's private vulnerability reporting (Security tab).
- An in-flight `@RequiredArgsConstructor` change to `RestlessRegistrar` would have dropped its
  `ObjectProvider<RestlessAuthorizationMetrics>` fallback (breaking resources with no
  Actuator/Micrometer on the classpath) and its null-default for `RestlessProperties` - caught
  before it was ever committed; `RestlessRegistrar` keeps its explicit constructor.

**Before this changelog existed:** see the git history (`cf0b117` onward) for the Cerbos
integration, exception handling, OpenAPI generation, and versioning work that predates this file.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). You may use, modify, and distribute
this project in compliance with the License; a copy of it is included in this repository.
