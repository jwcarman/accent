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
| CockroachDB | 24.1 | accepted | **skips** | `true` (unconditional) |
| YugabyteDB | 2024.1 | accepted | **skips** | `true` (unconditional) |
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

Four arms gate `true` on a measured floor, because `SKIP LOCKED` arrived in
each engine at a specific release:

| Platform | Floor | Comparison |
|---|---|---|
| `PostgreSQL` | 9.5 | major/minor |
| `MySQL` | 8.0 | major only (minor floor is 0) |
| `MariaDB` | 10.6 | major/minor |
| `Oracle` | 11 | major only |

Below its floor, an arm's `supportsSkipLocked()` returns `false` even though
the syntax itself may parse on older releases too — the floors are the
documented arrival version of the *clause*, not a re-verification that every
version below it rejects the syntax.

## Why CockroachDB, YugabyteDB, and H2 are unconditional

`CockroachDB` and `YugabyteDB` report a `version()` describing the PostgreSQL
release they emulate, not their own release number (see
[Platforms](platforms.md)) — there is no meaningful major/minor of the actual
engine to gate a floor on. Both were measured to genuinely skip at the one
version tested (CockroachDB 24.1, YugabyteDB 2024.1), so their arms return
`true` unconditionally; no lower bound is claimed.

H2 is unconditional for a different reason: no earlier H2 version was
available to test and no documented floor is known. H2 2.3.232 genuinely
skips, contradicting an earlier guess (`SPEC.md` §4.3) that it would not.

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
