# spring-boot-restless-example

A standalone Spring Boot application consuming `spring-boot-restless` exactly as an external
project would: real entities (`Employee`, `Department`, `Project`, `ProjectAssignment`), a real
Cerbos PDP, a real Keycloak realm issuing JWTs, an in-memory H2 database. Every capability the
framework offers is demonstrated somewhere in this app — this document is a map of what's where
and how to exercise it. For how the *framework itself* works, see the [root README](../README.md);
this one is scoped to this app specifically.

## Table of contents

- [Quick start](#quick-start)
- [Entities at a glance](#entities-at-a-glance)
- [Feature walkthrough](#feature-walkthrough)
- [Demo accounts](#demo-accounts)
- [Running the automated tests](#running-the-automated-tests)

## Quick start

```bash
# From the repo root - build everything once so this module's dependencies resolve.
../mvnw install

# Cerbos + Keycloak in the background (needed for the Cerbos-guarded routes below).
docker compose up -d      # from the repo root

# Run this app (port 8081, so it doesn't collide with Keycloak's own 8080).
../mvnw org.springframework.boot:spring-boot-maven-plugin:run -Dspring-boot.run.arguments=--server.port=8081
```

Swagger UI: `http://localhost:8081/swagger-ui.html`. Raw OpenAPI document:
`http://localhost:8081/v3/api-docs`. Full Keycloak/Cerbos setup and demo-account minting is in the
root README's [Running the full demo](../README.md#running-the-full-demo-docker-compose--keycloak)
section — the curl examples below assume you already have a token, exactly as shown there.

## Entities at a glance

| Entity | Tier | Base path | What it demonstrates |
|---|---|---|---|
| `Project` | Compile-time generated | `/projects` | codegen, auditing, API versioning, delegating-bean guard |
| `Department` | Manual, defaults | `/departments` | reflective defaults, PATCH, soft delete |
| `Employee` | Manual, hand-written | `/employees` | custom CUD logic, embed, custom read action, named view, field masking, DTO-aware Cerbos attributes, optimistic concurrency |
| `ProjectAssignment` | Compile-time generated | `/project-assignments` | many-to-many bridge, bulk create at scale, restricted operation set |

## Feature walkthrough

Every curl example below needs `TOKEN` set to a real Keycloak-issued JWT — see
[Demo accounts](#demo-accounts).

### CRUD, bulk create/update, multi-field sort

Every entity gets the full verb set (or a restricted one, see `ProjectAssignment` below) for free:
`POST`/`GET`/`PUT`/`DELETE` by id, `GET` list/page/overview/select, `POST bulk`/`PUT bulk`. Sort is
a repeatable query param: `GET /employees?sort=lastName,asc&sort=firstName,asc`.

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/employees?sort=lastName,asc" | jq
```

### PATCH — `Department`

Opt-in partial update: only the fields actually sent are changed. `DepartmentRestlessResource`
wires a plain `DefaultPatchDataSource` — no hand-written logic needed.

```bash
curl -s -X PATCH -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Research & Development"}' \
  http://localhost:8081/departments/1 | jq   # code is left untouched
```

### Soft delete — `Department`

`Department implements SoftDeletable`; its delete data source is `DefaultSoftDeleteDataSource`
instead of the hard-deleting default. A deleted department disappears from listings but a direct
fetch by id still works — flag-and-keep, not remove.

```bash
curl -s -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8081/departments/1
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8081/departments/1 | jq       # 200 - still there
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8081/departments | jq '.body'  # excluded from the list
```

### Optimistic concurrency / `If-Match` — `Employee`

`Employee.version` is a plain `@Version` field — a concurrent stale write already gets a 409 with
zero extra code. `EmployeeDto.version` is what lets a client opt further into the `If-Match`
precondition (412 on a stale value, checked before any write is attempted):

```bash
CURRENT=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8081/employees/1 | jq -r .version)

curl -s -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -H "If-Match: $CURRENT" \
  -d '{"firstName":"Ada","lastName":"Byron","email":"ada@example.com"}' \
  http://localhost:8081/employees/1 -w "\nHTTP %{http_code}\n"   # 200

# Same (now stale) If-Match again:
curl -s -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -H "If-Match: $CURRENT" \
  -d '{"firstName":"Ada","lastName":"King","email":"ada@example.com"}' \
  http://localhost:8081/employees/1 -w "\nHTTP %{http_code}\n"   # 412
```

### API versioning — `Project`

`@RestlessEntity(version = "1")` on `Project`, resolved by `ExampleApplication`'s
`apiVersioningConfigurer()` bean via the `X-API-Version` header — not required, so every other
route (unversioned) keeps working exactly as before this existed:

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H "X-API-Version: 1" http://localhost:8081/projects | jq
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/projects | jq   # no header - also fine
```

### Custom read action — `Employee`

`byEmailDomain`: a suffix `LIKE` the default equality filter can't express.

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/employees/actions/byEmailDomain?domain=restless-demo.example" | jq
```

### Named view — `Employee` `contact`

A different bounded-context shape of the same aggregate: `GET /employees/{id}/contact` returns a
reduced `EmployeeContactDto` (no salary, no departmentCode) instead of the default `EmployeeDto`.
Guarded independently — see [DTO-aware Cerbos attributes](#dto-aware-cerbos-attributes--employee-contact)
below.

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8081/employees/1/contact | jq
```

### `RestlessEmbed` — `Employee` → `Department`

Opt-in expansion of a related resource, resolved through *that* resource's own guard:

```bash
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8081/employees/1?expand=department" | jq
```

### Auditing — `Project`

`Project extends AbstractAuditableEntity`; `createdDate`/`lastModifiedDate` populate automatically
via Spring Data JPA's own auditing (`ExampleApplication`'s `@EnableJpaAuditing` is the other half).

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/projects/1 | jq '.createdDate, .lastModifiedDate'
```

### Cerbos row-scoping — `Employee`, `Project`

A `manager`'s JWT `scopedLastName` claim restricts which employees they see; row-scoping on
`Project` is resolved from the caller's *own* `Employee` row (not a JWT claim) — see the root
README's [step 5](../README.md#5-principal-attributes-that-arent-jwt-claims). Both come from the
same mechanism: `CerbosAuthorizationGuard.scope()` translating a Cerbos query plan into a JPA
`Specification`.

### Cerbos field masking — `Employee.salary`

`@CerbosHiddenField` + `CerbosFieldMasker`, driven by the `view` action's policy output: a manager
without the `canViewSalary` claim gets `salary: null`, unconditionally visible to admins.

### DTO-aware Cerbos attributes — `Employee` `contact`

`policies/employee.yaml`'s `contact` action rule references `request.resource.attr.initials` — a
field that exists only on the mapped `EmployeeContactDto`, never on the `Employee` entity itself.
`EmployeeRestlessResource`'s guard is wired with `CerbosAuthorizationGuard`'s DTO-aware constructor
(`CerbosDtoResourceAttributesMapper<Employee, EmployeeContactDto>`) specifically so this attribute
can reach the PDP at all:

```bash
curl -s -H "Authorization: Bearer $MANAGER_TOKEN" http://localhost:8081/employees/1/contact
# 200 only if the manager's own "expectedInitials" JWT claim matches this employee's computed initials
```

### Authorization-denial metrics

Every `AuthorizationGuard` denial increments `restless.authorization.denials` (tagged
`resource`/`action`/`hook`) — `spring-boot-starter-actuator` is already a dependency here, and
`application.properties` opts `metrics` into the actuator's web exposure (only `health` is exposed
by default), so this needs no extra wiring beyond that one property:

```bash
# A denial needs an *existing* row the caller isn't allowed to see - a missing id 404s before the
# guard's canAccess() ever runs, so it never reaches the counter at all. bob-manager is scoped to
# "Hopper" (see the demo-account table below), so fetching any other employee denies:
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $BOB_TOKEN" http://localhost:8081/employees/2   # 403, some non-Hopper employee's id

curl -s http://localhost:8081/actuator/metrics/restless.authorization.denials | jq
```

### `ProjectAssignment` — bulk create at scale, restricted operations

The many-to-many bridge between `Project` and `Employee`: `@RestlessEntity(operations = ...)`
excludes `UPDATE` entirely (a membership row is add/remove, never edited in place) — no route for
it gets registered at all, not just guarded off.

```bash
curl -s -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '[{"projectId":1,"employeeId":1},{"projectId":1,"employeeId":2}]' \
  http://localhost:8081/project-assignments/bulk | jq
```

### OpenAPI / Swagger

Every route above — hand-written and dynamically-registered alike — is fully documented at
`/v3/api-docs`/`/swagger-ui.html`, `RestlessOpenApiCustomizer`'s doing. Field-level `@Schema`
annotations on any DTO (see `EmployeeDto`) show up identically whether the route behind them is
hand-written or dynamic.

### The BOM and the test-support module

`pom.xml` imports `spring-boot-restless-dependencies` instead of hand-pinning
`spring-boot-restless`/`spring-boot-restless-cerbos`/`spring-boot-restless-test` versions
separately — this module is the one place in the reactor meant to look like a real external
consumer, so it's also the one place that demonstrates the BOM. `DepartmentDefaultCudTest` uses
`spring-boot-restless-test`'s `RestlessErrorAssertions`/`RestlessPageAssertions` instead of
hand-rolled `jsonPath` chains for the standard error/page shapes.

## Demo accounts

| username | role | notable claims |
|---|---|---|
| `alice-admin` | admin | — |
| `bob-manager` | manager | `scopedLastName=Hopper`, `canViewSalary=false` |
| `carol-manager` | manager | `scopedLastName=Lovelace`, `canViewSalary=true` |
| `dave-employee` | employee | — |

See the root README's [Running the full demo](../README.md#running-the-full-demo-docker-compose--keycloak)
for how to mint a token for each, and `docker/keycloak/README.md` for the full account table. The
`contact` named view's `expectedInitials`/manager `scopedLastName` claims used above are test-only
JWT claims (see `EmployeeAuthorizationGuardTest`) — the real Keycloak demo realm doesn't issue an
`expectedInitials` claim for `bob-manager`/`carol-manager`, so exercise that specific scenario via
the automated tests rather than a live curl, or mint your own token with a custom claim.

## Running the automated tests

```bash
../mvnw test   # from this directory, or -pl example from the repo root
```

Needs Docker running — several test classes start a real Cerbos PDP via Testcontainers
(`CerbosBackedTest`). No Keycloak needed for the test suite itself: `spring-security-test`'s
`jwt()` post-processor pre-populates the security context directly, without a real signed token.
