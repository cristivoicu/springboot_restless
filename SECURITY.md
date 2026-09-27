# Security Policy

## Supported Versions

`spring-boot-restless` is pre-1.0 (`0.0.1-SNAPSHOT`) and under active
development - only the latest commit on `main` is supported. There is no
backport policy yet; see [VERSIONING.md](VERSIONING.md) for what "pre-1.0"
means for API stability.

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for a security vulnerability.

Instead, report it privately via [GitHub's private vulnerability reporting]
(Security tab -> "Report a vulnerability" on this repository), or email the
address listed in the root `pom.xml`'s `<developers>` section. Include:

- A description of the vulnerability and its impact.
- Steps to reproduce (a minimal `@RestlessResource`/`@RestlessEntity` repro is
  ideal, given how much of this framework's surface is annotation-driven).
- Which module (`app`, `cerbos`, `processor`, ...) and version/commit are
  affected.

You should receive an acknowledgement within a few days. This is a
single-maintainer project run outside of paid time, so response time may vary
- please be patient, and thank you for reporting responsibly.

## Scope

Things that count as a security issue here:

- Authorization bypass in `AuthorizationGuard`'s three hook points
  (`preCheck`/`scope`/`canAccess`), `RestlessRegistrar`'s startup
  fail-fast-on-no-guard check, or the Cerbos-backed guard/field-masker in the
  `cerbos` module.
- Mass-assignment / unintended field exposure through the reflective
  `Default*DataSource` classes or the reflective default `Mapper` the
  annotation processor can generate.
- Anything that lets one `@RestlessResource` read/write another's data
  (cross-resource isolation).
- Injection via the reflection-driven default search filter
  (`getSpecification`) or `@RestlessEmbed` resolution.

Things that are **out of scope** (report as an ordinary bug/issue instead):

- Denial-of-service via an intentionally pathological request shape against a
  demo/example app (`example` module) not meant for production use as-is.
- Findings that require modifying policy YAML, application properties, or
  Java code you control to reach.
