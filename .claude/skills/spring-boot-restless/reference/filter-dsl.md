# Filter DSL, sort, and the `Specification` escape hatch

## Default filter

`getSpecification()` defaults to an equality predicate on every non-null, non-blank field
declared directly on the `SearchDto` subclass, ANDed together — zero code beyond declaring the
field.

**Primitive fields are always skipped**, not just when zero-valued — a primitive (e.g.
`boolean`) can never represent "the client didn't send this filter" (Java always defaults it).
Use a boxed type (`Boolean`) for an optional equality filter instead. An empty `Collection` (an
unset `In`-suffixed field) gets the same "absent" treatment as a blank string.

## Operator suffixes — zero extra code

A `SearchDto` field whose name ends in one of these filters with that operator instead of plain
equality, reflected over automatically. Wire format is camelCase, matching the Java field name
exactly (`?ageGte=30`, not `?age_gte=30`):

| Suffix | Operator | Example field | Example query |
|---|---|---|---|
| `Gte` | `>=` | `ageGte` | `?ageGte=30` |
| `Lte` | `<=` | `ageLte` | `?ageLte=65` |
| `Gt` | `>` | `salaryGt` | `?salaryGt=50000` |
| `Lt` | `<` | `salaryLt` | `?salaryLt=200000` |
| `Like` | contains-match | `nameLike` | `?nameLike=smith` |
| `Ne` | not-equal | `statusNe` | `?statusNe=CLOSED` |
| `In` | membership (repeatable param, binds to `List`/`Collection`) | `statusIn` | `?statusIn=OPEN&statusIn=PENDING` |

Two distinct failure modes for a misdeclared suffixed field: a base property that doesn't exist
on the entity at all is **silently ignored** (a `SearchDto`-author mistake, not client input); one
that exists but doesn't support the operator's type throws `IllegalStateException` (wrong on
every request, not a 400).

## Multi-field sort

`AbstractSearchDto#sort` is a repeatable query param, Spring Data's own convention:
`?sort=lastName,asc&sort=firstName,asc`. An unresolvable direction or a property that isn't an
actual entity field both become a clean 400 before any query runs.

## Beyond the seven operators

Override `getSpecification(SearchDto)` directly for joins, boolean `OR`, cross-field logic, or
anything else the suffix convention can't express. `RestlessSpecifications` is a small fluent
builder for that override:

```java
@Override
protected Specification<Thing> getSpecification(SearchDto dto) {
    ThingSearchDto s = (ThingSearchDto) dto;
    return RestlessSpecifications.<Thing>builder()
            .eq("status", s.getStatus())
            .gte("createdDate", s.getCreatedDateGte())
            .lte("createdDate", s.getCreatedDateLte())
            .build();
}
```

Or add a **named custom read action** (`getCustomReadActions()`, hand-wired tier only) alongside
the default filter for a genuinely different query/`SearchDto` shape, exposed at
`GET {basePath}/actions/{name}`.
