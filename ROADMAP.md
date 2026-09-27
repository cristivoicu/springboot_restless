# Roadmap

Tier 0 (adoptability) and Tier 1 (standards correctness) are done — see
[CHANGELOG.md](CHANGELOG.md). This tracks what's next, roughly in
recommended build order. Nothing here is scheduled; it's a priority-ordered
backlog with design notes, not a commitment.

## Next up

1. **[Write commands](docs/design/write-commands.md)** — ✅ done (hand-wired
   tier). `@RestlessEntity`/annotation-processor support remains open, see
   the doc's own "phase 2" note.
2. **[Filter DSL](docs/design/filter-dsl.md)** — ✅ done. Operator-suffix
   convention (`ageGte`/`nameLike`/`codeIn`/...) over the existing
   `SearchDto` reflection loop (`FilterOperator`), plus `RestlessSpecifications`,
   a small `Specification` builder for hand-written escape hatches - proven
   as a genuine drop-in by refactoring `GadgetRestlessResource`'s own
   hand-written filter/read-action onto it.
3. **[Keyset pagination](docs/design/keyset-pagination.md)** — offset
   pagination degrades at depth and shifts under concurrent writes; adds an
   opt-in `cursor=` mode alongside (not replacing) today's `page=`/`size=`.
   Independent of the above two; safe to build in parallel.

## Also worth doing, smaller

- **Idempotency keys** on `POST` (`Idempotency-Key` header,
  request-fingerprint-keyed dedupe table) - pairs naturally with write
  commands once those exist, since a non-idempotent command is exactly where
  a client most wants a safe-retry story.
- **Domain events / transactional outbox** - the natural companion to write
  commands (see that doc's "what this does not try to solve yet"). Design
  the publish hook when write commands ship even if the actual publisher
  wiring comes later.
- **GraalVM native-image support** - `RestlessRegistrar`'s runtime
  `registerMapping` and every reflection-based default (`Default*DataSource`,
  the generated `Mapper`, `getSpecification`) are invisible to native-image's
  static analysis without hand-written `RuntimeHints`. Real but bounded work;
  low priority unless a consumer actually asks for native-image support.

## Deliberately not scheduled (would need a decision this project hasn't made)

- **Maven Central publishing** - needs Sonatype/GPG credentials, a decision
  only the maintainer can make.
- **Kotlin support, GraphQL adapter, WebFlux port, OPA/Cedar guard
  backends, a conformance TCK** - each is a multi-week effort in its own
  right that would roughly double the surface area this project has to
  maintain. Worth reconsidering if and when there's a concrete consumer
  asking for one of these specifically, not speculatively.

## Docs

The full documentation restructure (`ARCHITECTURE.md`, `docs/reference/*`,
ADRs extracted from javadoc, etc.) proposed during the original library
review is a separate, standalone effort from the code work above - tracked
here as a reminder it's still open, not designed in detail yet.
