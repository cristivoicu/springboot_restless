# Contributing to spring-boot-restless

Thanks for considering a contribution. This is a small, opinionated framework
with a lot of design reasoning baked into its javadoc - reading a class's
javadoc before changing it will usually save you a round-trip.

## Before you start

For anything beyond a small fix (a typo, an obvious bug), please open an issue
first describing what you want to change and why. This project has explicit,
documented design boundaries (see the root `README.md`'s "Scope" section and
[VERSIONING.md](VERSIONING.md)) - a PR that crosses one of them without prior
discussion is likely to be declined even if the code itself is good.

## Development setup

- **Java 25** and **Maven 3.9+** (`./mvnw` is included, no local Maven install
  required).
- **Docker** and **Docker Compose**, only for the `cerbos` module's
  Testcontainers-backed tests and `example`'s Cerbos-backed test suite (every
  `EmployeeAuthorizationGuardTest`-style test starts a real Cerbos PDP
  container). Everything else runs against plain H2, no Docker needed.

```bash
./mvnw test              # whole reactor
./mvnw -pl app test       # one module
```

## Using Claude Code on this repo

`.claude/skills/spring-boot-restless/` is an Agent Skill that teaches Claude Code this
framework's conventions - the three-tier decision framework for adding an entity, authorization
guard/Cerbos wiring, write commands, bulk operations, and the filter DSL. It loads automatically
when relevant while working in this repo (e.g. in `example`). Copy the whole directory into a
consumer project's own `.claude/skills/` to get the same assistance there.

## Module map

See the root `README.md`'s "Modules" section - in short: `processor` (compile-time
codegen), `app` (the framework), `cerbos` (optional Cerbos-backed auth), `example`
(a realistic consumer app, also this project's end-to-end test bed).

## Code style

- Match the surrounding code's comment density and idiom - this codebase
  explains *why*, not just *what*, in javadoc; a new class with none at all
  reads as unfinished here.
- No new hand-written boilerplate where a `Default*` class or the annotation
  processor could generate it - if you're writing something every entity
  would need, it probably belongs in `app`, not in `example`.
- Every behavior change needs a test. `app`'s own fixtures
  (`Gadget`/`Gizmo`/`Sprocket`/`Doohickey`/...) exist so the framework can be
  tested in isolation, without `example` - prefer adding to those unless the
  change is specifically about realistic, business-named usage.

## Commit messages / PRs

- Keep commits focused; explain *why* in the body when the change isn't
  self-evident from the diff.
- Run `./mvnw test` for the whole reactor before opening a PR (or note which
  modules you couldn't run, e.g. "no Docker available locally").
- Update the relevant module's tests and, if user-facing, the root
  `README.md`/`example/README.md` in the same PR - a behavior change without
  a doc update is treated as incomplete.

## Reporting bugs vs. security issues

Security vulnerabilities go through [SECURITY.md](SECURITY.md), not a public
issue.
