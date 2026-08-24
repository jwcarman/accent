# accent

Which database is this JDBC connection actually talking to?

> Every one of these databases speaks SQL. They differ by *accent*.

`DatabaseMetaData.getDatabaseProductName()` answers a related question badly.
CockroachDB, YugabyteDB, and Amazon Redshift all report `PostgreSQL` when
reached through pgjdbc — they speak the wire protocol and satisfy the metadata
call identically, while differing on exactly the things callers branch on
(`CockroachDB`'s `FOR UPDATE` does not mean what PostgreSQL's means). And
MariaDB reached through `mysql-connector-j` reports `MySQL`; reached through
`mariadb-java-client` it reports `MariaDB` — the same server, two identities,
depending on a dependency choice made somewhere else. accent curates these
impostors into a sealed vocabulary, so a caller can `switch` over the result
and let the compiler force every case to be handled:

```java
Platform platform = Accent.of(dataSource);

// Most callers stop here — one measured predicate, not a switch.
String claimSql = platform.supportsSkipLocked()
    ? "SELECT ... FOR UPDATE SKIP LOCKED"
    : "SELECT ... FOR UPDATE";
```

Callers who need the exact per-platform SQL — not just a yes/no answer — can
switch exhaustively instead. Grouping is a feature of the sealed design: every
arm that genuinely skips a locked row shares one `case` label, because
`switch` doesn't care *why* seven different engines agree, only that they do:

```java
String claimSql = switch (platform) {
    case PostgreSQL _, CockroachDB _, YugabyteDB _, MySQL _, MariaDB _, Oracle _, H2 _
        -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case SqlServer s -> "SELECT ... WITH (UPDLOCK, READPAST)"; // a different statement, not a spelling
    case Db2 _, HSQLDB _, SQLite _, Derby _, Unknown _ -> "SELECT ... FOR UPDATE"; // safe fallback
};
```

Every arm in the first `case` is backed by a contention test — see
[`docs/observed-strings.md`](docs/observed-strings.md) for the measurement
behind each one, including why CockroachDB genuinely skips despite an earlier
guess (still visible in [`SPEC.md`](SPEC.md#1-what-it-is)) that it wouldn't.

Zero runtime dependencies. Plain JDBC only.

## Why detection issues `SELECT version()`

Every `DatabaseMetaData` signal that could distinguish CockroachDB from
PostgreSQL is *itself* a hidden server round trip — pgjdbc implements
`getSQLKeywords()`, the `getMax*NameLength()` family, and
`getDefaultTransactionIsolation()` with catalog queries against
`pg_get_keywords()` and `pg_settings`. And each is a weaker signal than the
explicit one. `getDefaultTransactionIsolation()` is the trap:
`default_transaction_isolation` is configurable, so a genuine PostgreSQL server
set to serializable reports the same `8` CockroachDB does — a false positive on
exactly the database you must not misidentify. An explicit `SELECT version()`
adds no I/O those alternatives avoid; it only makes the I/O legible at the call
site.

Measured against `postgres:13`, CockroachDB agrees with a real PostgreSQL
server on 122 of the 135 no-arg scalar `DatabaseMetaData` methods. Of the 13
that differ, two are environmental (`getURL`, `getUserName`), two are ordinary
version differences, and nine are genuine identity signals — but every one of
those nine is either a hidden round trip, a "-2 means unknown" non-answer that
breaks the day the engine starts answering, or a keyword list that shifts
between releases. `SELECT version()` is one explicit round trip, returning a
documented string that names the product outright: CockroachDB's answer does
not even begin with `PostgreSQL`. It is the seam, not a workaround. Detection
issues it *only* when `getDatabaseProductName()` reports `PostgreSQL` — no
other family pays for it. See [`docs/observed-strings.md`](docs/observed-strings.md)
for the full measurement, including the method-by-method comparison.

## The compatibility cost — read this before you `switch`

`Platform` is a **sealed** interface with no third-party extension point. That
is what makes the `switch` above exhaustive without a `default` arm, and
exhaustiveness at compile time is the entire value of this library — the
compiler, not a runtime guess, forces every caller to decide what happens for
a database accent hasn't been taught.

It has a real cost: adding a permitted subtype later (say, a future `Redshift`
arm — see below for why it isn't one of the thirteen yet) throws
`MatchException` at runtime in **already-compiled** exhaustive switches — the
compiler inserts an implicit throwing default when compiling against an older
accent, and that default fires when the jar is upgraded without a recompile.

**Include a `default` unless you pin the version.** If you switch
exhaustively without one, treat every accent upgrade as a potential binary
break and recompile against it. This is accepted as the honest price of
compile-time certainty, not something the library works around.

If you don't want an exhaustive switch over all thirteen arms at all, most
callers don't need one — call `platform.supportsSkipLocked()` for the one
capability accent currently exposes and skip the `switch` entirely.

## What accent is for — and is not

accent identifies databases and answers a small number of capability
questions about **spellings of the same operation**: `BYTEA` vs `BLOB` vs
`VARBINARY`, upsert syntax, identifier quoting. Differences like that really
are the same operation wearing a different dialect, and accent's `Platform`
`switch` is a reasonable place to pick between them.

It is **not** a license to unify operations whose *semantics* differ.
`FOR UPDATE SKIP LOCKED`, SQL Server's `WITH (UPDLOCK, READPAST)`, and Oracle's
skip-locked are not three spellings of one query — they are three statements
with genuinely different concurrency guarantees, and code that treats them as
interchangeable compiles everywhere and is subtly wrong on most of it. accent
does not paper over that: `supportsSkipLocked()` answers one narrow, verified
question (does a second transaction genuinely skip a row a first transaction
holds, rather than block on it?) and is deliberately not the first member of a
general capability bag. It is not a dialect/SQL-generation library (jOOQ
exists) and not a migration tool (Flyway and Liquibase exist).

### Db2: the strongest evidence for why this matters

Db2 12.1 accepts `FOR UPDATE SKIP LOCKED`. It parses. It executes without
error. A capability check that only asks "did this statement run?" would
conclude Db2 supports skip locked — and it would be wrong. A genuine
two-connection contention test (one connection holds a row lock in an open
transaction, a second issues `SELECT ... FOR UPDATE SKIP LOCKED` against both
rows) shows Db2 accepts the clause and **silently ignores it**, returning both
rows — including the one still locked by the first connection:

```
did not skip: returned [1, 2] — clause accepted and ignored
```

A caller doing outbox claiming against Db2 on the strength of the syntax being
accepted would hand the same row to two workers at once, silently, under
concurrency. That is exactly the failure `Platform.Db2#supportsSkipLocked()`
returning `false` exists to prevent, and it is the reason accent's
`supportsSkipLocked()` is backed by contention tests rather than parse checks
for every arm that claims `true`. Measured identically across three runs; see
the "SKIP LOCKED, by contention" section of
[`docs/observed-strings.md`](docs/observed-strings.md) for the full matrix.

## Why Redshift is absent

There is no Redshift container image — it is AWS-only — so a `Redshift` arm in
0.1.0 would be the one platform shipping on an unverifiable guess, inside a
hierarchy whose entire selling point is compile-time certainty backed by
measurement. accent's rule is: **an unverified arm is worse than no arm**,
because the sealed type invites callers to trust it. A connection to Redshift
falls into `Unknown` today. Adding `Redshift` later is a breaking change for
exhaustive switchers (see above) — that is the honest cost of not guessing,
and it's cheaper than shipping a wrong arm.

## The maintenance loop

Every heuristic here is pinned against strings actually observed from a
running database, recorded in
[`docs/observed-strings.md`](docs/observed-strings.md), and mirrored into unit
fixtures that pin the heuristics without Docker. Integration tests (`*IT`,
under failsafe) re-observe those same pairings against live containers.

When a driver or server upgrade changes what it reports, the integration test
for that pairing fails first — because the string it's asserting against has
changed underneath it. When that happens: update the heuristic in
`Detector`/`Platform` **and** the corresponding row in
`docs/observed-strings.md` together, in the same change. The fixture and the
prose are two views of one fact; let them drift and the next contributor is
back to guessing.

## Running the matrix locally

```
mvn verify
```

runs unit tests, the embedded engines (H2, HSQLDB, SQLite, Derby), and the
container tests for PostgreSQL, CockroachDB, YugabyteDB, MySQL, MariaDB
(through both `mariadb-java-client` and `mysql-connector-j`), and SQL Server.

```
mvn verify -Dexcluded.test.groups=
```

adds Oracle and Db2, which are excluded from the default run because they are
slow and licence-gated. Db2 publishes no `linux/arm64` manifest, so on Apple
Silicon it runs only under amd64 emulation and is noticeably slow; CI runs it
natively on `linux/amd64` runners, where it takes about 70 seconds (Oracle
takes about 15). All heavyweight containers need Docker running locally. The
full matrix — 60 unit tests and 23 integration tests, Oracle and Db2 included —
runs green on every pull request; CI does not exclude any test group.

## Coordinates

**Not yet released.** accent has not been published to Maven Central; there is
no `0.1.0` artifact to resolve yet. Once the first release ships, the
coordinates will be:

```xml
<dependency>
    <groupId>org.jwcarman.accent</groupId>
    <artifactId>accent</artifactId>
    <version>0.1.0</version>
</dependency>
```

Until then, the current version in `pom.xml` is `0.1.0-SNAPSHOT`, on the
`accent-0.1.0` branch, unpublished.

## Documentation

The [documentation site](https://jwcarman.github.io/accent/) publishes from
this repository's `main` branch and is not live yet — this work has not
merged there. Until it is, the same content lives in-repo:
[`docs/index.md`](docs/index.md) · [`docs/observed-strings.md`](docs/observed-strings.md) · [`SPEC.md`](SPEC.md) · [`CHANGELOG.md`](CHANGELOG.md)

## License

Apache License, Version 2.0. See [`LICENSE`](LICENSE).
