# spring-boot-restless

Annotation-driven REST CRUD for Spring Boot, built around DDD and volatility-based design:
each axis of change (persistence, mapping, HTTP wiring) is hidden behind its own stable
contract, so adding an entity means writing domain-meaningful code once and getting HTTP for
free — not hand-subclassing controllers.

## How it works

A `RestlessResourceHandler<E, K>` composes an entity's four `*DataSource` classes (create,
read, update, delete — each its own volatility, independently pluggable) plus its `Mapper`s and
`Specification` builder, and declares nine concrete, shared handler methods (`create`,
`findOne`, `findList`, `findPage`, `findPageOverview`, `findPageSelect`, `update`, `deleteById`,
`deleteAll`). At startup, `RestlessRegistrar` finds every bean annotated `@RestlessResource`,
resolves its entity/id/DTO types via reflection off its own generics, and registers each of
those nine methods as a live Spring MVC route (`RequestMappingHandlerMapping.registerMapping`) —
the same mechanism Spring Data REST uses. No code generation, no per-entity controller classes.

What you still write by hand, per entity (the DDD-meaningful, type-checked part):

- The JPA entity itself.
- `CreateDataSource` / `ReadDataSource` / `UpdateDataSource` / `DeleteDataSource` — one small
  class per verb, each wrapping the entity's `SpecificationRepository`.
- A `Mapper<Entity, Dto>` for the response shape.
- `CreateModel` / `UpdateModel` / `DeleteModel` / `SearchDto` marker-interface DTOs.
- One `RestlessResourceHandler` subclass wiring the above together, annotated
  `@RestlessResource(basePath = "...")`.

Nothing else. No controller class, no `@RequestMapping` methods, no manual route registration.

## Adding a new entity

Using `Department` (`src/main/java/.../entity/department/`) as the reference — it has no
hand-written controller at all, just these files:

1. **Entity** — `Department.java`, a plain `@Entity`.
2. **Repository** — `DepartmentRepository extends SpecificationRepository<Department, Long>`.
3. **DTOs** — `DepartmentDto` (response), `DepartmentCreateModel implements CreateModel`,
   `DepartmentUpdateModel implements UpdateModel`, `DepartmentDeleteModel implements DeleteModel`,
   `DepartmentSearchDto extends AbstractSearchDto` (adds filter fields; paging/sorting are
   inherited).
4. **Mapper** — `DepartmentMapper implements Mapper<Department, DepartmentDto>`.
5. **Data sources** — `DepartmentCreateDataSource`, `DepartmentReadDataSource`,
   `DepartmentUpdateDataSource`, `DepartmentDeleteDataSource`, each extending the matching
   abstract class from `controller.{create,read,update,delete}` and doing the actual
   `specificationRepository` calls.
6. **Resource bean** — `DepartmentRestlessResource extends RestlessResourceHandler<Department, Long>`,
   `@Component @RestlessResource(basePath = "/departments")`, wiring the five beans above
   through the handful of `getXDataSource()`/`getXMapper()`/`getSpecification()` accessor methods.

That's it — no `RestlessRegistrar` changes, no new routes to declare by hand. Once the context
starts, `/departments`, `/departments/{id}`, `/departments/list`, `/departments/overview`,
`/departments/select/async` all exist with the standard CRUD verbs.

## Project status

This is a working proof of the runtime-registration mechanism (Employee is mounted at
`/employees-dynamic` rather than `/employees` so the original hand-written reference
controllers can stay in place for parity testing — see `ErrorResponseParityTest`). See
`src/test/java/.../registry/` for the tests proving the mechanism: full route coverage,
cross-resource isolation, duplicate-`basePath` detection, and error-response parity against the
hand-written baseline.
