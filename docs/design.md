# Design

This page is the argument behind the API: why `Platform` is sealed, why
detection issues `SELECT version()`, why capability claims are contention-tested
instead of parse-checked, and the compatibility cost that comes with all of
it. Everything here is decided, not open — see `SPEC.md` and
`docs/superpowers/specs/2026-08-24-accent-design.md` in the repository for the
full record, including what was rejected and why.

## Why sealed, not enum, not an open interface

The prior art splits cleanly on one question: *may a third party add a
platform?*

| Library | Shape | Why |
|---|---|---|
| jOOQ `SQLDialect`, Spring Boot `DatabaseDriver` | enum | sole curator, ships often |
| Flyway `DatabaseType`, Liquibase `Database` | open interface + `ServiceLoader` | third-party extension is a feature |

accent is the sole curator of its own vocabulary, so **sealed** wins. It beats
an enum because a `record` per arm carries per-platform data — the reported
`Version`, and an overridden `supportsSkipLocked()` — extracted directly by
pattern matching at the branch, something an enum constant can't do as
cleanly. It beats an open interface because exhaustiveness at compile time
*is the product*: a caller who writes an exhaustive `switch` gets a compiler
error, not a runtime surprise, the day accent adds a platform they haven't
handled. Flyway and Liquibase's `ServiceLoader` extension points exist
because third-party dialects are a feature for them; accent has no analogous
need, and an open hierarchy would mean giving up the one property — forced,
compile-time handling of every known case — that makes the sealed design
worth having in the first place.

`Version` is a single nested value type shared by every arm
(`record Version(String productName, String productVersion, int majorVersion,
int minorVersion)`), rather than four fields repeated across thirteen
records. A fifth field, if one is ever needed, costs one edit instead of
thirteen; `Platform` exposes the four accessors as default methods delegating
to it.

## Why SELECT version

Every `DatabaseMetaData` signal that could distinguish CockroachDB from
PostgreSQL is *itself* a hidden server round trip. pgjdbc implements
`getSQLKeywords()`, the `getMax*NameLength()` family, and
`getDefaultTransactionIsolation()` by querying catalog tables —
`pg_get_keywords()`, `pg_settings` — so `Accent.of(metaData)` was never
query-free for the PostgreSQL family regardless of which signal detection
used. And each candidate is a weaker signal than an explicit query:

- `getDefaultTransactionIsolation()` is unusable as a tell.
  `default_transaction_isolation` is a configurable PostgreSQL setting, so a
  genuine PostgreSQL server set to serializable reports the same `8`
  CockroachDB does — a false positive on exactly the database accent must
  not misidentify.
- The `getMax*NameLength()` family reports `-2` ("unknown") for CockroachDB,
  not a CockroachDB-specific value. Detection resting on an engine declining
  to answer breaks silently the day the engine starts answering.
- `getSQLKeywords()` carries the strongest tell — CockroachDB's list
  includes words like `changefeed` and `backup` that PostgreSQL's doesn't —
  but the list is large and shifts between releases, so a heuristic pinned to
  it needs re-verifying on every CockroachDB upgrade.

Measured against `postgres:13`, CockroachDB 24.1 agrees with a real
PostgreSQL server on 122 of the 135 no-arg scalar `DatabaseMetaData` methods.
Of the 13 that differ, two are environmental (`getURL`, `getUserName`), two
are ordinary version differences, and the nine genuine identity signals are
the ones listed above — every one of them either a hidden round trip, a
non-answer that breaks the day the engine starts answering, or a keyword list
that shifts between releases. See the "How alike, exactly" section of
[Observed Strings](observed-strings.md) for the full method-by-method
comparison.

`SELECT version()` is one explicit round trip, returning a documented string
that names the product outright — CockroachDB's answer doesn't even begin
with `PostgreSQL`. It resolves the whole family (PostgreSQL, CockroachDB,
YugabyteDB) in a single query, is cheaper than `getSQLKeywords()`, and is
stable across releases in a way a keyword list is not. It is the seam, not a
workaround for a problem query-free detection could otherwise avoid. Detection
issues it *only* when `getDatabaseProductName()` reports `PostgreSQL` — no
other family pays for it.

## Why contention tests, not parse checks

An earlier design assumed that "does the statement execute without error"
was a sound proxy for "does this platform genuinely skip locked rows." It
isn't. Measured against live containers, every current-version server in
accent's matrix except HSQLDB, SQLite, and Derby *accepts*
`FOR UPDATE SKIP LOCKED` as syntax — including Db2, which accepts the clause
and **silently ignores it**, returning rows still locked by another
connection's open transaction. A parse check would report Db2 as supporting
skip-locked semantics. It does not: a genuine two-connection contention test
(one connection holds a lock, a second asserts it skips the locked row rather
than blocking) is the only test that tells `supportsSkipLocked()` the truth.
See [Capabilities](capabilities.md) for the full methodology and results
table.

This also settled two arms an earlier draft had guessed wrong in the opposite
direction. `SPEC.md` §1's original sketch treated `CockroachDB` as *not*
sharing PostgreSQL's skip-locked semantics — a guess since corrected: a
contention test shows CockroachDB genuinely skips. And `SPEC.md` §4.3 guessed
H2 would reject `SKIP LOCKED` outright; H2 2.3.232 not only parses it but
genuinely skips by contention. Neither correction would have been visible
from a parse check alone.

## The MatchException compatibility cost

`Platform` has no third-party extension point, which is what makes an
exhaustive `switch` over it valid without a `default` arm — and that
exhaustiveness is the entire value of the sealed design. It has a real,
accepted cost: adding a permitted subtype later (a future `Redshift` arm, for
instance — see [Platforms](platforms.md#why-redshift-is-absent) for why it
isn't one of the thirteen yet) throws `MatchException` at runtime in
**already-compiled** exhaustive switches. The compiler inserts an implicit
throwing default when compiling against an older accent, and that default
fires the moment the jar is upgraded without a recompile — the switch that
compiled cleanly against 0.1.0 can throw at runtime against 0.2.0 without a
single source-code change.

**Include a `default` unless you pin the version.** If you switch
exhaustively without one, treat every accent upgrade as a potential binary
break and recompile against it. This is accepted as the honest price of
compile-time certainty, not something accent works around — the sealed
design's entire selling point is that the compiler, not a runtime guess,
forces every caller to decide what happens for a database accent hasn't been
taught. A caller who doesn't want that trade-off at all doesn't need to take
it: `platform.supportsSkipLocked()` answers the one capability question most
callers actually have, without a switch.

## What accent is for — and is not

accent identifies databases and answers a small number of capability
questions about **spellings of the same operation**: `BYTEA` vs `BLOB` vs
`VARBINARY`, upsert syntax, identifier quoting. Differences like that really
are the same operation wearing a different dialect, and a `Platform` switch
is a reasonable place to pick between them.

It is **not** a license to unify operations whose *semantics* differ.
`FOR UPDATE SKIP LOCKED`, SQL Server's `WITH (UPDLOCK, READPAST)`, and
Oracle's skip-locked are not three spellings of one query — they are three
statements with genuinely different concurrency guarantees, and code that
treats them as interchangeable compiles everywhere and is subtly wrong on
most of it. accent is not a dialect/SQL-generation library (jOOQ exists) and
not a migration tool (Flyway and Liquibase exist).

## Why one predicate

`supportsSkipLocked()` answers one narrow, verified question and is
deliberately not the first member of a general capability bag. Every
predicate added to `Platform` is a promise to be correct on every platform
forever — verified by a contention test, not asserted — so the surface grows
only on demand, starting from the concrete need that motivated it
(`continuum-jdbc`'s outbox claiming) rather than a speculative list of
"capabilities a database might have."

## Why Redshift is absent

There is no Redshift container image — it's AWS-only — so a `Redshift` arm in
0.1.0 would be the one platform shipping on an unverifiable guess, inside a
hierarchy whose entire selling point is compile-time certainty backed by
measurement. accent's rule: **an unverified arm is worse than no arm**,
because the sealed type invites callers to trust it. A connection to Redshift
falls into `Unknown` today. Adding `Redshift` later is a breaking change for
exhaustive switchers — the honest cost of not guessing, and cheaper than
shipping a wrong arm.
