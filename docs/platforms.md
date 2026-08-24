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

This is why `CockroachDB#supportsSkipLocked()` and
`YugabyteDB#supportsSkipLocked()` are unconditional `true` rather than
version-gated: there is no meaningful major/minor of the actual engine to gate
on (see [Capabilities](capabilities.md)).

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

### YugabyteDB

Also reports `productName` = `PostgreSQL`. Distinguished by a `-yb-` marker
(case-insensitive) inside the `SELECT version()` result, e.g.
`PostgreSQL 11.2-YB-2024.1.0.0-b0 on ...`.

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
`productVersion` spans two lines — `Oracle AI Database 26ai Free Release
23.26.2.0.0 - Develop, Learn, and Run for Free\nVersion 23.26.2.0.0` — and the
marketing name and the release number disagree (26ai vs. 23.26). Use
`majorVersion()` / `minorVersion()` (23 / 26), not string parsing of
`productVersion`.

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
