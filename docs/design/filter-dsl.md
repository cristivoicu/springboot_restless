# Design: a filter DSL beyond equality-match

Status: **implemented** (`FilterOperator`/`RestlessSpecifications`, `app`
module - see `GizmoSearchDto`/`GizmoFilterTest` for the worked example, and
`GadgetRestlessResource`'s `getSpecification()`/`byEmailDomain` action for
`RestlessSpecifications` used as a drop-in replacement for hand-written
lambdas).

**Correction**: the sketch below originally assumed `?age_gte=30`
(snake_case) binds "for free" to a Java field named `ageGte` via
`ServletRequestDataBinder`. Traced through the actual binding code
(`bindSearchDto`) during implementation - this isn't true; plain
`ServletRequestDataBinder` does ordinary JavaBean property matching, no
snake_case translation. **The wire format actually shipped is camelCase,
matching the Java field name exactly** (`?ageGte=30`, `?nameLike=Jo`) - no
new binder configuration, consistent with how every other field in this
codebase already binds. The rest of this document is left as originally
written for the design reasoning; only this one factual claim was wrong.

## The gap today

`RestlessResourceHandler#getSpecification(SearchDto)` reflects over a
`SearchDto`'s declared fields and ANDs together `cb.equal(...)` for every
non-null one. That's it — no ranges, no `LIKE`, no `IN`, no null-checks, no
OR. The escape hatch is a named custom read action
(`GadgetEmailDomainSearchDto` + a hand-written `Specification` is the
worked example in `app`'s own fixtures) — which is the right answer for
genuinely bespoke logic, but the wrong answer for "I just want `createdAt
between X and Y`," a need that shows up in the first week of any real admin
UI and shouldn't require a new named route and a new DTO class every time.

## Three designs considered

### A. RSQL/FIQL query string (`?filter=age=ge=30;name==Jo*`)

The "GitHub API"-style approach. A grammar (`rsql-parser` on Maven Central, or
a hand-rolled ~150-line recursive-descent parser) turns the string into an
AST, which a small visitor turns into a `Specification<E>`.

- **Pro**: one query param, arbitrarily nested boolean logic (`and`/`or`,
  parenthesized), well-precedented (GitHub, Atlassian, and several Spring
  Data extensions use exactly this).
  **Con**: a real grammar to parse and, more importantly, to *secure* —
  every operator/property combination needs validating against the entity's
  actual fields and each field's actual Java type before it reaches a
  `CriteriaBuilder` call, or a client can probe for fields the `SearchDto`
  never declared. Heavier to implement and to document; overkill for the
  common case.

### B. Structured operator-suffix query params (`?age_gte=30&age_lte=50&name_like=Jo`)

Extend the existing "reflect over `SearchDto`'s declared fields" mechanism
instead of replacing it: a field suffix (`_gte`, `_lte`, `_gt`, `_lt`,
`_like`, `_ne`, `_in`) maps to an operator, applied only to fields the
`SearchDto` actually declares (so the existing whitelist-by-declaration
security property is preserved for free) and only to operators valid for
that field's Java type (`_gte`/`_lte` need `Comparable`, `_like` needs
`CharSequence`, `_in` needs the binder to accept a repeatable/comma-separated
param bound to `List<T>`).

- **Pro**: no new grammar, no new parser, no new security surface beyond what
  already exists — it's the same reflection loop `getSpecification` has
  today, with a switch on suffix instead of always `equal`. Every query
  param is self-documenting in an OpenAPI schema (`age_gte: integer`) the way
  a single opaque `filter=` string never is.
  **Con**: no boolean OR, no arbitrary nesting — "give me rows where either A
  or B" still needs a named custom read action. In practice this covers the
  large majority of real filter needs (ranges + partial match + membership),
  which is exactly the same 80/20 argument the framework already makes for
  equality-match defaults.

### C. A fluent `Specification` builder for the escape hatch itself

Not a query-string DSL at all — a small helper (`RestlessSpecifications`)
that makes *hand-written* `getSpecification()` overrides and custom read
actions shorter to write, e.g.:

```java
return RestlessSpecifications.<Gadget>builder()
    .eq("lastName", dto.getLastName())
    .like("email", dto.getEmailDomain(), "%@" + dto.getEmailDomain())
    .between("createdAt", dto.getFrom(), dto.getTo())
    .build();
```

- **Pro**: zero new HTTP-facing surface, zero new security consideration
  (it's just a boilerplate-reduction utility for code an entity author
  already writes by hand), can ship in an afternoon.
  **Con**: doesn't give a generic REST client anything new — it only helps
  the Java author, not the "GET with query params" caller.

## Recommendation: B first, C alongside it, A only if asked for

Ship **B** as the actual answer to "the default is equality-only" — it's
additive (existing equality behavior for a field with no suffix variant is
unchanged), reuses the current security model, and is the smallest surface
that covers range/partial-match/membership, the three gaps people actually
hit. Ship **C** at the same time since it's nearly free and makes every
hand-written escape hatch (present and future) shorter. Only build **A**
(RSQL) if a real consumer asks for boolean OR/nesting — it's a strictly
bigger, riskier surface for a need B doesn't cover.

## Sketch of B's implementation

`getSpecification`'s reflection loop already walks `searchDto.getClass().getDeclaredFields()`.
Extend it to also look for sibling fields matching `{name}Gte`, `{name}Lte`,
`{name}Like`, `{name}In`, `{name}Ne` (Java-identifier-safe suffixes, not raw
query-param underscores — `ServletRequestDataBinder` already maps
`?age_gte=30` to a property named however the DTO declares it, so the
*wire* convention and the *Java field name* convention can differ; simplest
is to keep them the same and just document `age_gte` as the wire name for a
field literally named `ageGte` — no new binder configuration needed).

```java
for (Field field : searchDto.getClass().getDeclaredFields()) {
    Operator op = Operator.fromFieldNameSuffix(field.getName()); // GTE/LTE/LIKE/IN/NE/EQ
    String baseProperty = op.stripSuffix(field.getName());
    if (!entityHasProperty(baseProperty)) continue; // ignore, don't 400 - same "absent means unset" idiom
    predicates.add(op.buildPredicate(cb, root.get(baseProperty), value));
}
```

A per-type validity check (`GTE`/`LTE` requires `Comparable`, `LIKE`
requires `CharSequence`) throws the same `ResponseStatusException(400, ...)`
the sort-property check in `pageableOf` already uses for an analogous
mistake, at startup-adjacent request time rather than as an opaque Hibernate
failure later.

## Files touched (estimate)

- `app/.../resource/RestlessResourceHandler.java` (extend `getSpecification`'s reflection loop; new `Operator` enum)
- A new `app/.../specification/RestlessSpecifications.java` (design C's builder)
- `RestlessOpenApiCustomizer` (document the suffix convention per filterable field)
- Tests: one fixture `SearchDto` with a `Gte`/`Lte`/`Like`/`In` field each, proving each operator and proving an operator on the wrong Java type 400s cleanly.

Smaller than write-commands; a good second or parallel-track item.
