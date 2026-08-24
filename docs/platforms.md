# Platforms

`Platform` is a sealed interface with thirteen arms, one of them `Unknown`. Detection
reads `DatabaseMetaData.getDatabaseProductName()` case-insensitively, then
disambiguates by product version or (for the PostgreSQL family only) a
`SELECT version()` round trip. Every string below is measured, not assumed —
see [Observed Strings](observed-strings.md) for the full capture.

## The impersonation warning, read this first

**`CockroachDB` and `YugabyteDB` report a `version()` describing the
PostgreSQL release they emulate, not their own release number.** Through
pgjdbc, CockroachDB 24.1 reports `productVersion` = `13.0.0`; YugabyteDB
2024.1 reports `11.2-YB-2024.1.0.0-b0`. Neither number is the engine's own
version. `platform.majorVersion()` on a `CockroachDB` instance is PostgreSQL's
13, not CockroachDB's 24. Do not write comparisons like
`cockroach.majorVersion() >= 24` — there is no `24` to compare against.

Both arms carry a second component for this reason: `engine()`, a
`Platform.EngineVersion` (`raw`, `major`, `minor`) parsed out of the same
`SELECT version()` string detection already fetches — `v(\d+)\.(\d+)` for
CockroachDB, `-YB-(\d+)\.(\d+)` for YugabyteDB. `engine().raw()` is the only
way a caller learns the real CockroachDB or YugabyteDB version, since
`version()` never will. `CockroachDB#supportsSkipLocked()` and
`YugabyteDB#supportsSkipLocked()` gate on `engine()`'s major/minor, not on
`version()`'s: CockroachDB's floor (22.2) is a discovered boundary — v22.1.22
genuinely rejects `SKIP LOCKED`, v22.2.19 and above genuinely skip — while
YugabyteDB's floor (2.16) is only the lowest version measured, not a proven
line. See [Capabilities](capabilities.md) for the full version-series
evidence, and never deconstruct either record positionally on the assumption
it still has one component.

## The thirteen arms

### PostgreSQL

Reports `productName` = `PostgreSQL`. Detected only after `SELECT version()`
rules out CockroachDB and YugabyteDB — metadata alone cannot separate the
three, since CockroachDB's bare `13.0.0` version string is indistinguishable
from a genuine PostgreSQL 13 server at the four `DatabaseMetaData` fields
accent reads. `version()` for real PostgreSQL begins with `PostgreSQL`.

### CockroachDB

Also reports `productName` = `PostgreSQL` through pgjdbc — the load-bearing
impostor case. Distinguished by `SELECT version()`, whose result does not
begin with `PostgreSQL` at all: `CockroachDB CCL v24.1.32 (...)`. Detector
matches case-insensitively on `cockroachdb` appearing anywhere in that string.
`version()` was needed because nothing else works well: the other candidate
signals (`getSQLKeywords()`, `getMax*NameLength()`,
`getDefaultTransactionIsolation()`) are either hidden round trips already, an
"unknown" non-answer that breaks the day the engine starts answering, or a
keyword list that shifts between releases — see
[Design](design.md#why-select-version).

The same `SELECT version()` string also carries CockroachDB's own version,
which `engine()` parses out with `v(\d+)\.(\d+)` — `v24.1.32` becomes major
24, minor 1. `supportsSkipLocked()` gates on this, not on `version()`: `true`
when `engine().major() > 22`, or major `== 22` and `engine().minor() >= 2`.
This floor is a discovered boundary — contention testing found v22.1.22
genuinely rejects `SKIP LOCKED` (`ERROR: unimplemented: SKIP LOCKED lock wait
policy is not supported`) while v22.2.19 and above genuinely skip. See
[Capabilities](capabilities.md).

### YugabyteDB

Also reports `productName` = `PostgreSQL`. Distinguished by a `-yb-` marker
(case-insensitive) inside the `SELECT version()` result, e.g.
`PostgreSQL 11.2-YB-2024.1.0.0-b0 on ...`.

`engine()` parses YugabyteDB's own version out of that same string with
`-YB-(\d+)\.(\d+)` — `-YB-2.16.9.0-b0` becomes major 2, minor 16;
`-YB-2024.1.0.0-b0` becomes major 2024, minor 1. `supportsSkipLocked()` gates
on `engine()`, `true` when `major() > 2` or major `== 2` and `minor() >= 16`.
Unlike CockroachDB's floor, 2.16 is **not** a discovered boundary — it is the
lowest version that was measured (2.14.17 would not start on the test
machine). See [Capabilities](capabilities.md).

### MySQL

Reports `productName` = `MySQL`, after ruling out MariaDB reached through
`mysql-connector-j` (see below). No version-string marker is needed for the
genuine-MySQL case — it's what's left once MariaDB is ruled out.

### MariaDB

Two distinct reporting shapes, both measured:

- Through `mariadb-java-client`: `productName` = `MariaDB` directly.
- Through `mysql-connector-j`: `productName` = `MySQL`, and only
  `productVersion` names it — `11.4.12-MariaDB-ubu2404`. Detector checks for
  `mariadb` (case-insensitive, `contains`) in the product version whenever the
  product name says `MySQL`, and routes to `MariaDB` if it's present. This is
  the other load-bearing impostor case: the same server, same query, two
  different `productName` values depending on which driver jar is on the
  classpath.

### SqlServer

Reports `productName` starting with `Microsoft SQL Server` (prefix match,
case-insensitive). Measured as `Microsoft SQL Server` / `16.00.4265` against
SQL Server 2022.

### Oracle

Reports `productName` starting with `Oracle` (prefix match). Its
`productVersion` *may* span two lines, and does on 18c and later —
`gvenzl/oracle-free:23-slim-faststart` reports `Oracle AI Database 26ai Free
Release 23.26.2.0.0 - Develop, Learn, and Run for Free\nVersion 23.26.2.0.0`,
where the marketing name and the release number disagree (26ai vs. 23.26). But
11g XE reports a single line: `Oracle Database 11g Express Edition Release
11.2.0.2.0 - 64bit Production`. A heuristic anchored with `$` or one that
requires a newline breaks on one of those two shapes or the other. Use
`majorVersion()` / `minorVersion()`, not string parsing of `productVersion` —
accent is unaffected by either shape for exactly this reason.

`supportsSkipLocked()`'s floor (major 11) is the lowest version measured, not
a discovered boundary: 11.2.0.2, 18.4.0, 21.3.0, and 23.26 all genuinely skip
by contention test, and no Oracle 10g image is published, so nothing below
11.2.0.2 could be tested. Note also that Oracle 18c reports
`getDatabaseMinorVersion()` as `0` despite being release 18.4 — harmless
today, since this floor is major-only, but worth knowing before adding a
minor-version comparison to it. See [Capabilities](capabilities.md).

### Db2

Reports `productName` with a host-architecture suffix — `DB2/LINUXX8664`,
`DB2/NT64`, and others accent has not seen. Detector uses a `db2` prefix
match, not equality, because the suffix varies by platform. `productVersion`
(`SQL120100`) is a build identifier, not a dotted version — do not attempt to
parse it; `majorVersion()`/`minorVersion()` (12 / 1) are the only trustworthy
numeric source.

### H2

Reports `productName` = `H2` (exact match). Measured as `H2` / `2.3.232
(2024-08-11)`.

`supportsSkipLocked()`'s floor (2.2) is a discovered boundary: a classpath
matrix across five H2 versions (H2 is not containerisable, so each version's
jar was run in turn against the same contention harness) found 1.4.200,
2.0.206, and 2.1.214 all genuinely reject `FOR UPDATE SKIP LOCKED`, while
2.2.224 and 2.3.232 genuinely skip. H2 reports its own version honestly, so
unlike `CockroachDB` or `YugabyteDB` this gates directly on
`majorVersion()`/`minorVersion()` — no `engine()` component. See
[Capabilities](capabilities.md).

### HSQLDB

Reports `productName` = `HSQL Database Engine`. Detector matches a `hsql`
prefix.

### SQLite

Reports `productName` = `SQLite` (exact match).

### Derby

Reports `productName` = `Apache Derby`. Detector matches `derby` anywhere in
the name (`contains`).

### Unknown

Not a detected engine — the fallback arm. See [Unknown](unknown.md) for why
it exists, what it means, and how to supply your own resolution for a
database accent doesn't recognise.

## Why Redshift is absent

There is no Redshift container image — it's AWS-only — so a `Redshift` arm in
0.1.0 would be the one platform shipping on an unverifiable guess, inside a
hierarchy whose entire selling point is compile-time certainty backed by
measurement. A connection to Redshift falls into `Unknown` today, or into
whatever a caller-supplied fallback returns.

## Detection order matters

Detector checks the impostor cases before the platform they impersonate:
CockroachDB and YugabyteDB are resolved before falling through to
`PostgreSQL`, and MariaDB-via-`mysql-connector-j` is resolved before falling
through to `MySQL`. Matching is case-insensitive throughout; five of the ten
name comparisons use prefix or `contains` checks where that's what the driver
actually guarantees (Db2's arch suffix, Oracle's multi-line version, Derby's
unspecified exact string), and the rest use equality, established by
reconnaissance against a real product name.
