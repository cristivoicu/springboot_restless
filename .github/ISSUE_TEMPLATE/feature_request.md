---
name: Feature request
about: Propose a new capability or an extension point
title: ''
labels: enhancement
assignees: ''
---

**The problem this would solve** — what are you trying to do today that the framework has no
clean seam for?

**Proposed shape** — if you have one in mind (a new attribute on `@RestlessEntity`, a new hook on
`AuthorizationGuard`, a new `*DataSource` method, ...). It's fine to leave this open if you just
want to describe the problem.

**Does it fit this project's [Scope](../../README.md#scope)?** This framework deliberately keeps
response shape (`Mapper`), authorization (`AuthorizationGuard`), and anything the filter DSL can't
express out of its own generated/defaulted surface, on purpose — see `CONTRIBUTING.md`'s "Before
you start" section. A proposal that crosses one of those boundaries isn't automatically declined,
but it needs a stronger case made for it up front.

**Alternatives considered** — anything you tried working around with today's API, and why it
didn't fit.
