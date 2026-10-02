# spring-boot-restless

**Annotation-driven, DDD-friendly REST CRUD for Spring Boot — generate the boilerplate, hand-write the authorization.**

[![CI](https://github.com/cristivoicu/springboot_restless/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/cristivoicu/springboot_restless/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-0.0.1--SNAPSHOT-orange?logo=apachemaven&logoColor=white)](https://central.sonatype.com/)
[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

> **Status:** pre-1.0 (`0.0.1-SNAPSHOT`). Not yet published to Maven Central — build and install locally (see [Installation](#installation)). See [VERSIONING.md](VERSIONING.md) for what stability guarantees apply before `1.0`.
>
> **Looking for more depth?** This README is a concise reference. For a full tutorial-depth
> walkthrough of every mechanism — with sequence diagrams, the three-tier decision framework, and
> worked examples for each feature below — see [`docs/DEEP_DIVE.md`](docs/DEEP_DIVE.md).

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
- [Contributing](#contributing)
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

For the full, worked, runnable version of every capability above — including a real Cerbos +
Keycloak authorization demo — see the [`example`](example/README.md) module and
[`docs/DEEP_DIVE.md`](docs/DEEP_DIVE.md).

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull
request, and note that this project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). In
short:

1. Fork the repository and create a feature branch off `main`.
2. Run `./mvnw install` and `./mvnw test` locally before submitting (Docker must be running — a
   few test classes start a real Cerbos PDP via Testcontainers).
3. Keep changes scoped and covered by tests; update `CHANGELOG.md` for any user-facing change.
4. Open a pull request describing the change and its motivation.

See [SECURITY.md](SECURITY.md) to report a vulnerability privately rather than via a public issue.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). You may use, modify, and distribute
this project in compliance with the License; a copy of it is included in this repository.
