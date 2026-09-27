# Design: keyset (cursor) pagination

Status: proposed, not implemented.

## The problem with what exists today

`findPage`/`findPageOverview`/`findPageSelect` all go through
`pageableOf(SearchDto)` → Spring Data `PageRequest.of(page, size, sort)` →
`ReadDataSource#findAll(spec, pageable)`. That's offset pagination:
`LIMIT size OFFSET page*size`. Two well-known problems, both real at this
framework's own target scale (an admin UI or internal tool backed by a table
that grows past a few hundred thousand rows):

1. **Deep pages get slow.** `OFFSET 100000` still makes the database walk
   and discard 100,000 rows before returning the requested page — an
   `O(offset)` cost per request, not `O(size)`.
2. **Pages shift under concurrent writes.** If row 21 is deleted between a
   client fetching page 1 (rows 1–20) and page 2 (rows 21–40), the old row 41
   silently becomes the new row 40 and the client never sees it — or, worse,
   sees row 21's replacement twice.

Keyset pagination fixes both: instead of "skip N rows," the client sends
"give me the next page *after* this specific row," encoded as that row's own
sort-key values. The database then does an indexed range scan
(`WHERE (sortkey) > (lastSeenValue)`), which is `O(size)` regardless of how
deep the client has paged, and is stable under concurrent inserts/deletes
before the cursor position (new rows there don't shift anything after it).

## Why this framework is unusually well positioned for it

`AbstractSearchDto#getPageable()` already defaults to `Sort.by(ASC, "id")`
when the client sends no `sort` param at all — which means **every existing
route already sorts by a unique column by default**. Keyset pagination
*requires* a unique tie-breaker in the sort (otherwise "the row after this
one" is ambiguous when several rows share the same sort-key values) — most
frameworks retrofitting this have to bolt on an enforced tie-breaker; here
it's already the default behavior, just needs to become *required* rather
than *incidental* when cursor mode is requested.

## Shape

A new, distinct response envelope — reusing `PageableResponse` would be
wrong, because `totalElements`/`totalPages` require a `COUNT(*)` query keyset
pagination is specifically trying to avoid paying for on every request:

```java
public class CursorPageableResponse<B> {
    private B body;
    private String nextCursor;   // null when this is the last page
    private boolean hasMore;
}
```

Request side: a new optional query param, `cursor` (opaque, base64), read
instead of `page` when present. `size` stays meaningful (LIMIT). A resource
opts a route into cursor mode either by:

- **a query-parameter switch** on the existing `/page` routes (no cursor
  param → today's offset behavior, unchanged, zero migration burden on
  existing consumers; a `cursor` param present → keyset mode) — recommended,
  since it needs no new route registration and every existing client keeps
  working untouched, or
- a dedicated route suffix (`/page/cursor`) if keeping the two modes'
  request/response shapes fully separate in OpenAPI is preferred.

The query-parameter-switch approach is recommended: same route, same
`AuthorizationGuard.Action.READ_PAGE`, same `getSpecification`/`scope`
composition — cursor mode changes *only* how the `Pageable`-equivalent is
built and how the result is packaged, nothing about authorization or
filtering.

## The actual query-building problem

Spring Data's `Pageable`/`PageRequest` has no concept of "value to seek past"
— only `page`/`size`/`sort`. Keyset mode needs to build the seek predicate by
hand, as a `Specification<E>` ANDed onto the existing filter/scope
specification, same composition idiom `withScope`/`excludeSoftDeleted`
already use.

For a single sort column (the common case, e.g. `sort=id,asc`):

```java
(root, query, cb) -> cb.greaterThan(root.get("id"), lastSeenId)
```

For a multi-column sort (`sort=lastName,asc&sort=id,asc` — the tie-breaker
case), the seek predicate is a lexicographic "greater than" over a tuple,
which expands to an OR-of-ANDs, not a single comparison:

```
(lastName > :lastName)
OR (lastName = :lastName AND id > :id)
```

This generalizes to N sort columns as N clauses, each clause being "the
first `k` columns equal, column `k+1` strictly greater" (flipping
`greaterThan`/`lessThan` per-column when that column's own direction is
`DESC`). This is a genuinely reusable ~40-line utility
(`KeysetSpecifications.seekPast(sortOrders, lastSeenValues)`) that has
nothing entity-specific about it — build it once, generic over `Sort` and a
`Map<String, Object>` of last-seen values.

## Cursor encoding

Base64 of a small JSON object: `{"lastName": "Hopper", "id": 42}` — the
property names and values of the sort columns from the *previous* page's
last row. Not cryptographically signed for v1 (a forged cursor just seeks
from wherever the client claims, which is no more powerful than a legitimate
client already crafting `?sort=`/`?page=` params today — the existing
`getSpecification`/`scope` composition still applies to whatever the cursor
seeks past, so this is not an authorization bypass); signing/HMAC-ing the
cursor is a reasonable v2 hardening step if a consumer wants to prevent
clients reverse-engineering internal sort-key values, not a correctness or
security requirement for v1.

A malformed/undecodable cursor is a clean 400 (`ResponseStatusException`),
same "translate to 400 by hand at the boundary" idiom `pageableOf`/
`convertId`/`readBody` already use.

## What doesn't come for free

- **No `totalElements`/`totalPages` in cursor mode.** This is the actual
  trade-off keyset pagination makes, not an implementation gap — computing
  the total defeats the point (it's the same expensive full-table
  count/scan cursor pagination exists to avoid). `CursorPageableResponse`
  intentionally has no such field; a consumer who genuinely needs an
  approximate total should call `/list`'s cousin or a dedicated count
  endpoint separately, understanding that's a different cost profile.
- **No "jump to page 47" UX.** Keyset pagination is inherently sequential
  (next/previous only) - it's the right fit for infinite-scroll/API-to-API
  consumption, not for a UI with numbered page links. This is exactly why it
  should be additive (opt-in via `cursor=`) rather than a replacement for
  offset paging, which stays the right choice for that UI pattern.

## Files touched (estimate)

- `app/.../models/CursorPageableResponse.java` (new)
- `app/.../specification/KeysetSpecifications.java` (new, the seek-predicate builder)
- `app/.../resource/RestlessResourceHandler.java` (`paginate(...)`'s cursor-mode branch; cursor encode/decode helpers)
- `RestlessOpenApiCustomizer` (document the `cursor` param and the alternate response schema)
- Tests: multi-page walk-through proving stability under a concurrent insert/delete between two cursor fetches (the actual property offset pagination lacks), plus a malformed-cursor-is-400 test.

Medium-sized, self-contained; doesn't touch authorization, the annotation
processor, or any existing route's default behavior. Safe to build in
parallel with the filter DSL work.
