# accent — design

**Date:** 2026-08-24
**Status:** approved, pre-implementation
**Supersedes nothing.** Companion to `SPEC.md`, which remains the authoritative
statement of *why* accent exists. This document records the decisions `SPEC.md`
§9 left open, and only those.

---

## 1. Resolutions to SPEC.md §9

### 1.1 Version components are nested, not repeated (§9.1)

Each arm carries a single `Version` component. `Platform` exposes the four
accessors as default methods delegating to it.

```java
public sealed interface Platform {
    Version version();
    default String productName()    { return version().productName(); }
    default String productVersion() { return version().productVersion(); }
    default int majorVersion()      { return version().majorVersion(); }
    default int minorVersion()      { return version().minorVersion(); }

    record Version(String productName, String productVersion,
                   int majorVersion, int minorVersion) {}

    record PostgreSQL(Version version) implements Platform {}
    // ... one per arm
}
```

One component per record means a fifth field costs one edit rather than
thirteen. The cost is one extra level in deconstruction patterns
(`case MySQL(Version(_, _, int major, _))`); callers who want a scalar can use
the accessor and a guard instead (`case MySQL m when m.majorVersion() >= 8`).

`Version`'s canonical constructor validates: product name and version
non-null, major and minor non-negative.

`Unknown` needs no additional components. `Version` already carries the raw
strings §3.2 requires for actionable logging and gap reports.

### 1.2 Capabilities live on `Platform` (§9.3)

`supportsSkipLocked()` is a default method on `Platform` returning `false`,
overridden per arm where the answer is true or version-dependent.

One entry point, one import, and the common case is one call from
`Accent.of(dataSource)`. The cost — identity and capability sharing a type —
is accepted because the alternative asks every caller to learn a second
concept to answer the one question the library was built for. The surface
stays small per §4.3: `supportsSkipLocked()` and nothing else until a second
consumer demands more.

### 1.3 0.1.0 ships thirteen arms (§9.2)

`PostgreSQL`, `CockroachDB`, `YugabyteDB`, `MySQL`, `MariaDB`, `SqlServer`,
`Oracle`, `Db2`, `H2`, `HSQLDB`, `SQLite`, `Derby`, `Unknown`.

`Redshift` is omitted per §6.3 — it cannot be containerised, and an unverified
arm is worse than no arm inside a hierarchy that sells compile-time certainty.

Oracle and Db2 are in, despite slow images and licence acceptance, because
§3.3 makes every later addition a binary-breaking change for exhaustive
switchers. They are verifiable; they are merely inconvenient. Inconvenience is
handled by a build profile, not by deferral. See §3.2 below.

### 1.4 Minor version is load-bearing (§9.4)

`SKIP LOCKED` arrived in PostgreSQL 9.5 and MariaDB 10.6. Major alone cannot
express either boundary, so `Version` keeps `minorVersion`. Patch is not
retained: no capability boundary known to accent falls on a patch release, and
`DatabaseMetaData` does not expose one.

## 2. Decisions this document adds

### 2.1 `Version` is raw driver output, always

For impostor platforms the driver reports the *emulated* version, not the
engine's own. pgjdbc reports a PostgreSQL version number for CockroachDB;
mysql-connector-j prefixes MariaDB's version with the `5.5.5-` compatibility
hack. So `majorVersion()` on a `CockroachDB` is not CockroachDB's major
version.

accent reports exactly what the driver said, and says so bluntly in javadoc on
both `Version` and every impostor arm.

"Raw" is a promise that survives every driver release. "True engine version"
is a promise that breaks the first time a vendor changes its version string.
If reconnaissance (§3.1) shows the real version is reliably recoverable, it
can be added later as a separate component — a source-compatible addition,
unlike a new permitted subtype.

Because of this, capability predicates on impostor arms must not naively
compare against the raw version. Each impostor arm decides its own answer,
verified by §2.2.

### 2.2 Capability claims are executed, not asserted

Integration tests do not merely assert that `supportsSkipLocked()` returns the
value the arm hardcodes — that is a tautology. They **execute**
`SELECT ... FOR UPDATE SKIP LOCKED` against the live container and assert the
outcome agrees with the predicate.

This converts §4.3's "promise to be correct on every platform forever" from an
assertion into something CI re-checks on every driver and server bump. It is
also how the CockroachDB and YugabyteDB answers get settled empirically rather
than from memory.

### 2.3 Detection is a pure function behind a package-private seam

`Accent` is the only public entry point. Detection lives in a package-private
type whose input is a `Version` and whose output is a `Platform` — no JDBC
types, no I/O. That is what reaches the 100% coverage §6.5 demands, with no
Docker and no mocking beyond `DatabaseMetaData` at the `Accent` boundary.

Ordering is part of the contract: impostor checks run before the host they
impersonate (CockroachDB and YugabyteDB before PostgreSQL, MariaDB before
MySQL). Matching is case-insensitive and uses prefix or contains checks, never
equality, per §5.

Failure policy per §4.1: a `SQLException` surfaces as unchecked
`AccentException` carrying the cause. `Unknown` is never returned for a
connection failure — "could not ask" and "asked, did not recognise" stay
distinct facts.

## 3. Build order

### 3.1 Phase 0 is reconnaissance, and its output is data

§5 forbids implementing against its hypothesis table. So the first phase is a
throwaway harness that stands up every container and driver pairing and prints
the exact `getDatabaseProductName()`, `getDatabaseProductVersion()`,
`getDatabaseMajorVersion()` and `getDatabaseMinorVersion()` each one reports.

Its output is committed twice, per §6.4:

- `docs/observed-strings.md` — human-readable, dated, with image tags and
  driver versions, so a future reader knows what was true and when.
- unit-test fixture constants — the same strings, driving the fast suite.

The harness itself is throwaway. Nothing in `src/main` is written against the
§5 table.

### 3.2 Phases

| Phase | Work |
|---|---|
| 0 | Recon harness; observed strings recorded |
| 1 | `Platform`, `Version`, `AccentException` — unit TDD against observed strings |
| 2 | Detection heuristics — unit TDD, 100% coverage |
| 3 | `Accent` entry points over `DataSource` / `Connection` / `DatabaseMetaData` |
| 4 | Integration tests: identity per pairing, plus executed capability checks (§2.2) |
| 5 | Scaffolding, README, CHANGELOG, docs, CI |

Test tiers:

- **fast** — unit tests plus the four embedded engines (H2, HSQLDB, SQLite,
  Derby). No Docker. Exhaustive, not sampled, per §6.2.
- **default `mvn verify`** — adds container ITs for PostgreSQL, CockroachDB,
  YugabyteDB, MySQL, and MariaDB through both drivers, plus SQL Server.
- **`heavy` profile / JUnit tag** — Oracle and Db2. CI runs everything.

Rows 2, 3 and 6 of §6.1 — CockroachDB via pgjdbc, YugabyteDB via pgjdbc,
MariaDB via mysql-connector-j — are written first. They are the cases no
product-name check survives, and they are the reason the library exists.

### 3.3 Scaffolding

Mirrors `~/IdeaProjects/continuum` per §8: single module, `maven.compiler.release`
25, Spotless google-java-format bound to `validate`, `release`/`ci`/`license`
profiles, the three GitHub workflows, mkdocs-material built `--strict`,
Keep a Changelog, bare semver with no `v` prefix, pom version permanently
`-SNAPSHOT`. Sonar `projectKey` is `jwcarman_accent`.

## 4. Non-goals

Unchanged from `SPEC.md` §7.
