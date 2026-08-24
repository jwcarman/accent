# Capabilities

accent exposes exactly one capability predicate: `Platform#supportsSkipLocked()`.
It's deliberately narrow — a promise to be correct on every platform forever
is expensive, and accent adds to that surface only on demand (see
[Design](design.md#why-one-predicate)).

## What it answers

> Does this platform have genuine skip-locked semantics for
> `FOR UPDATE SKIP LOCKED`: a second transaction reading rows locked by a
> first must **skip** them rather than block?

It does **not** cover SQL Server's `WITH (UPDLOCK, READPAST)` — a different
statement with different semantics — and a `false` answer is always safe: a
caller falls back to plain `FOR UPDATE`, which blocks instead of skipping but
is never incorrect.

## Why parsing isn't enough

The obvious test — does the database accept the statement without error? —
is a parse check, and parse checks lie. Every current-version server in
accent's matrix except HSQLDB, SQLite, and Derby accepts
`FOR UPDATE SKIP LOCKED` as syntax. That includes Db2, which is the reason
this predicate exists in this shape.

Db2 12.1 parses `FOR UPDATE SKIP LOCKED` without error. A capability check
that only asked "did this statement run?" would report `true` — and it would
be wrong. A genuine two-connection contention test tells the truth: Db2
accepts the clause and **silently ignores it**, returning both rows,
including the one still locked by another connection's open transaction:

```
did not skip: returned [1, 2] — clause accepted and ignored
```

A caller doing outbox claiming against Db2 on the strength of the syntax
being accepted would hand the same row to two workers at once, silently,
under concurrency. That's exactly the failure `Db2#supportsSkipLocked()`
returning `false` exists to prevent — and it's the reason every arm that
claims `true` is backed by a contention test, not a parse check.

## The methodology

The contention harness (`SkipLockedContention.skipsLockedRows`, exercised by
`SkipLockedContentionIT` and `SkipLockedContentionHeavyIT`):

1. Connection one locks row 1 with plain `FOR UPDATE` and holds it open in a
   transaction.
2. Connection two runs `SELECT ... ORDER BY id FOR UPDATE SKIP LOCKED`
   against both rows, with a 5-second query timeout.
3. Genuine skip-locked semantics return **row 2 only**. Anything else — both
   rows (accepted and ignored), a timeout (blocked instead of skipping), or
   an exception (plain `FOR UPDATE` itself rejected) — is recorded as "does
   not skip."

## Measured results

| Engine | Version tested | Accepts the syntax | Skips by contention | `supportsSkipLocked()` |
|---|---|---|---|---|
| PostgreSQL | 17 | accepted | **skips** | `true` (floor: 9.5) |
| MySQL | 8.4 | accepted | **skips** | `true` (floor: 8.0) |
| MariaDB | 11.4 | accepted | **skips** | `true` (floor: 10.6) |
| CockroachDB | 24.1 | accepted | **skips** | `true` (floor: 22.2 of its own version) |
| YugabyteDB | 2024.1 | accepted | **skips** | `true` (floor: 2.16 of its own version) |
| H2 | 2.3.232 | accepted | **skips** | `true` (unconditional) |
| Oracle | 23 | accepted | **skips** | `true` (floor: 11) |
| Db2 | 12.1 | accepted | **does not skip — accepted and ignored** | `false` |
| SQL Server | 2022 | rejects `FOR UPDATE`/`FOR UPDATE SKIP LOCKED` outright | does not skip | `false` |
| HSQLDB | 2.7 | rejected | does not skip | `false` |
| SQLite | 3.47 | rejected (no row-locking model) | does not skip | `false` |
| Derby | 10.17 | rejected | does not skip | `false` |

Source: the "SKIP LOCKED, by contention" section of
[Observed Strings](observed-strings.md), which also records the exact
rejection and skip-and-ignore messages.

## Version floors

Six arms gate `true` on a measured floor, because `SKIP LOCKED` arrived in
each engine at a specific release:

| Platform | Floor | Comparison |
|---|---|---|
| `PostgreSQL` | 9.5 | major/minor |
| `MySQL` | 8.0 | major only (minor floor is 0) |
| `MariaDB` | 10.6 | major/minor |
| `Oracle` | 11 | major only |
| `CockroachDB` | 22.2 (of its own version — see below) | major/minor of `engine()` |
| `YugabyteDB` | 2.16 (of its own version — see below) | major/minor of `engine()` |

Below its floor, an arm's `supportsSkipLocked()` returns `false` even though
the syntax itself may parse on older releases too. For `PostgreSQL`, `MySQL`,
`MariaDB`, and `Oracle` these floors are more than a documented arrival
version of the clause: contention testing below each one now confirms the
rejection directly — PostgreSQL 9.4.26, MySQL 5.7.44, and MariaDB 10.5.29 all
fail with a SQL syntax error on `SKIP`/`SKIP LOCKED` (see [Observed
Strings](observed-strings.md)).

`CockroachDB` and `YugabyteDB` are different in kind, not just measurement:
their floors are not expressible from `version()` at all — see the next
section.

## CockroachDB and YugabyteDB: floors on `engine()`, not on `version()`

`CockroachDB` and `YugabyteDB` report a `version()` describing the PostgreSQL
release they emulate, not their own release number (see
[Platforms](platforms.md)). CockroachDB reports `productVersion` = `13.0.0`
at v22.1.22, v22.2.19, v23.1.30, *and* v24.1.32 alike — the same bare number
whether or not `SKIP LOCKED` genuinely works. There is no major/minor in
`version()` to gate a floor on.

Both arms therefore carry a second component, `engine()`, an `EngineVersion`
record (`raw`, `major`, `minor`) parsed out of the `SELECT version()` string
detection already fetches: `v(\d+)\.(\d+)` for CockroachDB, `-YB-(\d+)\.(\d+)`
for YugabyteDB. `raw` is independently useful — it is the only way a caller
learns the real CockroachDB or YugabyteDB version at all, since `version()`
never will. `supportsSkipLocked()` gates on `engine()`'s major/minor, the
same comparison shape as `PostgreSQL` or `MariaDB`, just against a different
source of truth. If `engine()` could not be parsed, `major`/`minor` are both
`0` and `supportsSkipLocked()` answers `false` — an unparseable version is no
evidence of capability, never a guess.

**CockroachDB's floor (22.2) is a discovered boundary.** Contention testing
across the version series found v22.1.22 genuinely rejects the clause
(`ERROR: unimplemented: SKIP LOCKED lock wait policy is not supported`) while
v22.2.19, v23.1.30, and v24.1.32 all genuinely skip. That is a real line
between "works" and "does not work," located by measurement.

**YugabyteDB's floor (2.16) is *not* a discovered boundary — it is the
lowest version measured.** 2.16.9, 2.18.9, 2.20.12, and 2024.1 (which parses
as major 2024, minor 1 — correctly above the floor) all genuinely skip.
2.14.17 would not start on the test machine, so nothing below 2.16.9 was ever
tested. Do not read `YugabyteDB`'s 2.16 the way `CockroachDB`'s 22.2 reads:
one is a proven boundary, the other is simply where measurement stopped.

## Why H2 is unconditional

H2 is unconditional for a different reason than CockroachDB or YugabyteDB: no
earlier H2 version was available to test and no documented floor is known. H2
2.3.232 genuinely skips, contradicting an earlier guess (`SPEC.md` §4.3) that
it would not.

## Why SqlServer and Db2 are `false` on purpose

**SqlServer** never appears as "accepted" for `FOR UPDATE SKIP LOCKED` in the
first place — SQL Server has no such clause. Its nearest equivalent, `WITH
(UPDLOCK, READPAST)`, is a different statement with different semantics and
outside what this predicate covers. A contention test against the exact
`FOR UPDATE SKIP LOCKED` clause confirms plain `FOR UPDATE` itself is
rejected outside a cursor declaration (`FOR UPDATE clause allowed only for
DECLARE CURSOR`), so no skip is ever observed. Do not "fix" this arm to
`true` on the strength of `READPAST`.

**Db2** is `false` because it fails the contention test specifically, despite
passing the parse check — see "Why parsing isn't enough" above. This is the
strongest evidence in the whole project for testing by contention rather than
by syntax acceptance.

Both `false` values are safe: a caller falls back to plain `FOR UPDATE`,
which blocks instead of skipping but never causes the double-claim bug that a
wrong `true` would.
