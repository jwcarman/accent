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

---

## 5. Amendments after Phase 0 reconnaissance (2026-08-24)

Recon ran. It refuted part of `SPEC.md` §5 and part of §2 above. Measurements
are in `docs/observed-strings.md`; the decisions they forced are here.

### 5.1 Detection requires a query, not only metadata

`SPEC.md` §5 hypothesised that CockroachDB's version string contains
`CockroachDB`. It does not. Through pgjdbc, CockroachDB reports
`productName` = `PostgreSQL`, `productVersion` = `13.0.0`, major 13, minor 0 —
nothing in the four identity fields separates it from a genuine PostgreSQL
13.0.0 server.

Other `DatabaseMetaData` methods *do* differ; measured against `postgres:13`,
nine of 135 scalar methods carry an identity signal (`docs/observed-strings.md`,
"How alike, exactly"). None is a usable seam:

- Each is a hidden server round trip. pgjdbc implements `getSQLKeywords()`, the
  `getMax*NameLength()` family and `getDefaultTransactionIsolation()` with
  catalog queries, so `of(DatabaseMetaData)` was never query-free for this
  family. An explicit query adds no I/O these avoid.
- `getDefaultTransactionIsolation()` false-positives: `default_transaction_isolation`
  is configurable, so a PostgreSQL server set to serializable reports the same
  `8` CockroachDB does.
- The `-2` name lengths mean "unknown", not "CockroachDB". Detection resting on
  an engine declining to answer breaks the day it answers.
- `getSQLKeywords()` content is the strongest tell but is a large result that
  shifts between CockroachDB releases.

`SELECT version()` resolves the whole family in one round trip. CockroachDB's
result does not even begin with `PostgreSQL`.

**Decision.** All three entry points from §4.1 survive. Detection issues
`SELECT version()` **only when `productName` is `PostgreSQL`** — no other family
needs it, and no other family pays for it. `of(DatabaseMetaData)` remains
viable because JDBC guarantees `DatabaseMetaData.getConnection()`.

The pure core widens from `Version` to a `Fingerprint`:

```java
record Fingerprint(String productName, String productVersion,
                   int majorVersion, int minorVersion,
                   String versionQuery) {}   // null unless the family needed it

Platform detect(Fingerprint fingerprint)     // still pure, still 100%-coverable
```

`Version` is unchanged as the public value type on each arm (§1.1).
`Fingerprint` is internal: detection input, never returned to callers.

### 5.2 An explicit override is a first-class path

Prior art from the Nessy session: their shipped-then-deleted `JdbcDialect`
threw on an unrecognised product, and the throw had to name two escapes because
callers sitting under someone else's `DataSource` hit strings no resolver
anticipates — pgbouncer, Aurora, H2 in PostgreSQL-compatibility mode.

**Decision.** 0.1.0 ships a caller-supplied fallback, consulted when detection
would otherwise return `Unknown`. "Detection failed, here is how to tell me the
answer" becomes API rather than an exception message. `Unknown` remains the
result when no fallback is supplied — the compile-time forcing function of
§3.2 is unchanged.

### 5.3 The capability test in §2.2 was too weak

§2.2 said capability claims are executed rather than asserted. Correct in
spirit, wrong in instrument. Every current server measured — including
CockroachDB, YugabyteDB and H2, all three of which `SPEC.md` §4.3 guessed would
not — **accepts** `FOR UPDATE SKIP LOCKED`. Parsing is not semantics. Executing
the statement on one connection proves only that it is not a syntax error.

**Decision.** The capability IT is a genuine contention test: two connections,
one holding a row lock in an open transaction, the second asserting it skips
that row rather than blocking. That is the property a caller doing outbox
claiming actually depends on, and it is the only test that can tell
`supportsSkipLocked()` the truth.

Until that test runs, no arm's `supportsSkipLocked()` value is settled. The
values are an output of Phase 4, not an input to Phase 1.

### 5.4 Integration tests must pin the JDBC driver explicitly

`com.yugabyte.Driver` also accepts `jdbc:postgresql:` URLs. With both jars on
the classpath, `DriverManager` may route a pgjdbc URL to the YugabyteDB driver.
The first Yugabyte measurement was silently taken through the wrong driver and
had to be redone with an explicit `new org.postgresql.Driver()`.

**Decision.** Integration tests construct the driver explicitly rather than
going through `DriverManager`, or keep the YugabyteDB driver off the test
classpath. A driver/server matrix that silently tests the wrong pairing is
worse than no matrix — it reports confidence it has not earned.

### 5.5 Scope note for the README

Nessy's session drew a line worth adopting in accent's own docs. accent is for
differences that are **spellings of the same operation** — `BYTEA` vs `BLOB` vs
`VARBINARY`, upsert syntax, identifier quoting. It is not a licence to unify
operations whose **semantics** differ: `FOR UPDATE SKIP LOCKED`, `READPAST`,
and Oracle's skip-locked are not three spellings of one query, and code that
treats them as such compiles everywhere and is subtly wrong on most of it.

This is the honest framing of what `supportsSkipLocked()` is for: it answers
whether one specific property holds, and it is deliberately not the first
member of a general capability bag (§4.3).
