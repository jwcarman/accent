# Observed driver strings

What real drivers actually reported, captured by the Phase 0 reconnaissance
harness. **This file, not `SPEC.md` §5, is what the detection heuristics are
written against.** §5's table was a hypothesis; this is measurement.

Every value below is verbatim from `DatabaseMetaData`. When an integration test
starts failing after a version bump, the string here has changed, and both the
heuristic and the unit fixture need updating together (`SPEC.md` §6.4).

**Captured:** 2026-08-24
**Host arch:** arm64 (Apple Silicon)

## Embedded engines

| Engine | Driver | `getDatabaseProductName()` | `getDatabaseProductVersion()` | major.minor |
|---|---|---|---|---|
| H2 | h2 2.3.232 | `H2` | `2.3.232 (2024-08-11)` | 2.3 |
| HSQLDB | hsqldb 2.7.4 | `HSQL Database Engine` | `2.7.4` | 2.7 |
| SQLite | sqlite-jdbc 3.47.1.0 | `SQLite` | `3.47.1` | 3.47 |
| Derby | derby 10.17.1.0 | `Apache Derby` | `10.17.1.0 - (1913217)` | 10.17 |

### SKIP LOCKED, executed

Statement executed against a real table, not assumed.

| Engine | Accepted syntax | Result |
|---|---|---|
| H2 | `SELECT ... FOR UPDATE SKIP LOCKED` | **accepted** |
| HSQLDB | — | rejected: `unexpected token: SKIP` |
| SQLite | — | rejected: `near "UPDATE": syntax error` |
| Derby | — | rejected: `Encountered "SKIP" at line 1, column 40` |

H2 parsing `FOR UPDATE SKIP LOCKED` contradicts `SPEC.md` §4.3's guess that H2
does not support it. The statement is accepted; whether H2's row locking gives
the concurrent-claim semantics a caller wants is a separate question, and is
why the arm's answer is recorded here rather than assumed.

## Containers, via the driver that matters

| # | Server (image) | Driver | `productName` | `productVersion` | major.minor |
|---|---|---|---|---|---|
| 1 | `postgres:17` | pgjdbc 42.7.12 | `PostgreSQL` | `17.10 (Debian 17.10-1.pgdg13+1)` | 17.10 |
| 2 | `cockroachdb/cockroach:latest-v24.1` | **pgjdbc 42.7.12** | `PostgreSQL` | `13.0.0` | 13.0 |
| 3 | `yugabytedb/yugabyte:2024.1.0.0-b129` | **pgjdbc 42.7.12** | `PostgreSQL` | `11.2-YB-2024.1.0.0-b0` | 11.2 |
| 4 | `mysql:8.4` | mysql-connector-j 9.2.0 | `MySQL` | `8.4.11` | 8.4 |
| 5 | `mariadb:11.4` | mariadb-java-client 3.5.2 | `MariaDB` | `11.4.12-MariaDB-ubu2404` | 11.4 |
| 6 | `mariadb:11.4` | **mysql-connector-j 9.2.0** | `MySQL` | `11.4.12-MariaDB-ubu2404` | 11.4 |
| 7 | `mcr.microsoft.com/mssql/server:2022-latest` | mssql-jdbc 12.8.1 | `Microsoft SQL Server` | `16.00.4265` | 16.0 |

Rows 2, 3 and 6 are the impostors — the reason accent exists.

### Row 6 confirms SPEC.md §5

MariaDB reached through `mysql-connector-j` reports `productName` = `MySQL`,
and the only thing separating it from row 4 is `MariaDB` appearing in
`productVersion`. Case-insensitive *contains* on the version string is a sound
disambiguator here.

Note also that `getDatabaseMajorVersion()` returns **11**, not 5. The
`5.5.5-` compatibility prefix that older MariaDB servers prepended is not
present in this pairing, and mysql-connector-j 9.2.0 reports the true server
major. Do not write a heuristic that depends on seeing `5.5.5-`.

### Row 3 confirms the hypothesis for YugabyteDB

`productVersion` contains `-YB-`. Metadata alone is sufficient.

**Driver-registration hazard found while measuring this row.** The YugabyteDB
driver (`com.yugabyte.Driver`) also accepts `jdbc:postgresql:` URLs, and when
both jars are on the classpath `DriverManager` may hand a `jdbc:postgresql:`
URL to the YugabyteDB driver instead of pgjdbc. The first measurement of row 3
was silently taken through the wrong driver. The value above was re-measured
with an explicit `new org.postgresql.Driver()`. accent's integration tests must
do the same, or keep the YugabyteDB driver off the test classpath entirely.

### Row 2 refutes the hypothesis for CockroachDB — the important finding

`SPEC.md` §5 guessed CockroachDB's version string would contain `CockroachDB`.
**It does not.** Through pgjdbc, CockroachDB is:

```
productName    = PostgreSQL
productVersion = 13.0.0
major.minor    = 13.0
```

There is nothing in `DatabaseMetaData` that separates this from a real
PostgreSQL 13.0.0 server. Not the product name, not the version string, not the
major or minor. The one metadata-level tell is a *negative* signal — real
pgjdbc-reported PostgreSQL versions carry a build suffix such as
`(Debian 17.10-1.pgdg13+1)` while CockroachDB reports a bare `13.0.0` — and a
build suffix is not something any vendor guarantees.

**CockroachDB cannot be detected from the four identity fields accent reads.**
It is not, however, wholly indistinguishable through `DatabaseMetaData` — see
"How alike, exactly" below, which measures that claim rather than asserting it.

## Beyond metadata: what the server will tell you when asked

One query settles the entire PostgreSQL family:

| Engine | `SELECT version()` |
|---|---|
| PostgreSQL | `PostgreSQL 17.10 (Debian 17.10-1.pgdg13+1) on aarch64-unknown-linux-gnu, compiled by gcc ...` |
| CockroachDB | `CockroachDB CCL v24.1.32 (aarch64-unknown-linux-gnu, built 2026/07/22 12:34:17, go1.22.12 ...)` |
| YugabyteDB | `PostgreSQL 11.2-YB-2024.1.0.0-b0 on aarch64-unknown-linux-gnu, compiled by clang ...` |

CockroachDB's `version()` does not even begin with `PostgreSQL`. This is an
unambiguous, cheap, single-round-trip disambiguator for the whole family.

Other signals measured, and why `version()` wins:

| Query | PostgreSQL | CockroachDB | YugabyteDB |
|---|---|---|---|
| `SHOW server_version` | `17.10 (Debian ...)` | `13.0.0` | `11.2-YB-2024.1.0.0-b0` |
| `SHOW server_version_num` | `170010` | `130000` | `110002` |
| `current_setting('crdb_version')` | error | `CockroachDB CCL v24.1.32 ...` | error |

`current_setting('crdb_version')` is a precise CockroachDB tell, but it
identifies exactly one engine and costs an exception on every other. `version()`
is one query that names every member of the family, so it is the better seam.

## SKIP LOCKED, executed against live servers

Not asserted from a table — the statement was run and the outcome recorded.

| Engine | Accepted syntax | Result |
|---|---|---|
| PostgreSQL 17 | `FOR UPDATE SKIP LOCKED` | accepted |
| CockroachDB 24.1 | `FOR UPDATE SKIP LOCKED` | accepted |
| YugabyteDB 2024.1 | `FOR UPDATE SKIP LOCKED` | accepted |
| MySQL 8.4 | `FOR UPDATE SKIP LOCKED` | accepted |
| MariaDB 11.4 | `FOR UPDATE SKIP LOCKED` | accepted |
| SQL Server 2022 | `WITH (UPDLOCK, READPAST)` | accepted |
| H2 2.3 | `FOR UPDATE SKIP LOCKED` | accepted |
| HSQLDB 2.7 | — | rejected |
| SQLite 3.47 | — | rejected |
| Derby 10.17 | — | rejected |

**Parsing is not semantics.** Every current-version server in the matrix except
the three embedded holdouts accepts the syntax, which means "does this statement
execute" is a weaker test than it looked when `SPEC.md` §4.3 was written. It
proves the statement is not a syntax error; it does not prove concurrent claims
skip locked rows rather than blocking. A capability predicate that a caller
trusts for outbox claiming needs a genuine two-connection contention test, not a
parse check.

## Heavy images

Both ran on arm64. Db2 has no `linux/arm64` manifest and the pull first 404s;
Docker Desktop then falls back to amd64 emulation and the container starts.
Treat Db2 as CI-verified (amd64 runners) rather than reliably reproducible on
an Apple Silicon workstation.

| Server (image) | Driver | `productName` | `productVersion` | major.minor |
|---|---|---|---|---|
| `gvenzl/oracle-free:23-slim-faststart` | ojdbc11 23.6.0.24.10 | `Oracle` | *(two lines — see below)* | 23.26 |
| `icr.io/db2_community/db2:12.1.0.0` | jcc 4.34.30 | `DB2/LINUXX8664` | `SQL120100` | 12.1 |

### Oracle's product version contains a newline

Verbatim, including the embedded line break:

```
Oracle AI Database 26ai Free Release 23.26.2.0.0 - Develop, Learn, and Run for Free
Version 23.26.2.0.0
```

Two consequences. Any fixture holding this string must preserve the newline, so
it belongs in a text block rather than a quoted literal. And any heuristic that
inspects Oracle's version string must not assume a single line — a
`contains`-style check is safe, a `startsWith`/full-match or a regex anchored
with `$` is not.

Note also that the marketing name (`Oracle AI Database 26ai`) and the release
number (`23.26.2.0.0`) disagree. `getDatabaseMajorVersion()` reports 23. Trust
the integer accessors, not the prose.

### Oracle's product version does not always span two lines

The class javadoc once claimed flatly that Oracle's product version "spans
two lines." That is false for 11g. A version matrix across every obtainable
Oracle image shows the newline is conditional on the release:

| Oracle version (image) | `productVersion` | major.minor | Skips by contention |
|---|---|---|---|
| 11.2.0.2 (`gvenzl/oracle-xe:11.2.0.2-slim`) | **single line**: `Oracle Database 11g Express Edition Release 11.2.0.2.0 - 64bit Production` | 11.2 | **skips** |
| 18.4.0 (`gvenzl/oracle-xe:18.4.0-slim`) | two lines | **18.0** | **skips** |
| 21.3.0 (`gvenzl/oracle-xe:21.3.0-slim`) | two lines | 21.3 | **skips** |
| 23.26.2 (`gvenzl/oracle-free:23-slim-faststart`) | two lines | 23.26 | **skips** |

11g Express Edition reports its version on one line with no embedded newline
at all. 18c and later report the two-line marketing-name-plus-release-number
shape recorded above under "Heavy images." A heuristic that assumes one shape
universally — an anchored-with-`$` check, or one that requires a newline —
breaks on whichever shape it did not assume. accent is unaffected because it
uses the integer accessors (`majorVersion()`/`minorVersion()`), never
string-parses `productVersion`, which is the point of using them.

Also note: **Oracle 18c reports `getDatabaseMinorVersion()` as `0`** despite
being release 18.4. Harmless today, since `Platform.Oracle`'s floor is
major-only, but it would silently misbehave for anyone who later adds a
minor-version comparison to that floor.

Every row above genuinely skips by contention test — 11.2.0.2 is the lowest
version measured, and no Oracle 10g image is published, so nothing below it
could ever be tested. This is the same shape of evidence as YugabyteDB's
floor, not CockroachDB's or H2's: the floor is bounded by measurement, not a
discovered boundary. accent's `OracleIT` and
`SkipLockedContentionFloorsIT#oracle11Point2SkipsLockedRowsAndSaysSo` exercise
the top and bottom of this table, respectively.

### Db2's product version is not a version number

`SQL120100` is a build identifier, not a dotted version. Nothing can be parsed
out of it by splitting on `.`, and code that tries will silently produce
garbage. `getDatabaseMajorVersion()` / `getDatabaseMinorVersion()` report 12
and 1 correctly, so the integer accessors are the only usable source here.

`productName` is `DB2/LINUXX8664` — the arch-specific suffix `SPEC.md` §5
predicted. A case-insensitive `DB2` **prefix** match is the right test; equality
would fail on every platform variant.

### SKIP LOCKED

| Engine | Accepted syntax | Result |
|---|---|---|
| Oracle 23 | `FOR UPDATE SKIP LOCKED` | accepted |
| Db2 12.1 | `FOR UPDATE SKIP LOCKED` | accepted |

Db2 accepted the standard spelling; the `SKIP LOCKED DATA` variant was not
needed. As with every other row in this file, acceptance is a parse result and
not evidence about locking semantics.

## Summary of what recon changed

| `SPEC.md` §5 hypothesis | Verdict |
|---|---|
| MariaDB via mysql-connector-j reports `MySQL`, version contains `MariaDB` | **confirmed** |
| YugabyteDB via pgjdbc identifiable from version string | **confirmed** (`-YB-`) |
| CockroachDB via pgjdbc: version string contains `CockroachDB` | **refuted** — bare `13.0.0`, needs `SELECT version()` |
| Db2 product name is `DB2/...`, prefix match | **confirmed** (`DB2/LINUXX8664`) |
| H2 does not support `SKIP LOCKED` (§4.3) | **refuted** — H2 2.3 parses it |
| Redshift | **unverifiable**, not shipped (§6.3) |

Two engines could not be measured and are not shipping arms in 0.1.0: Redshift
(no image exists) and nothing else. Every other arm in the vocabulary now rests
on a string observed above rather than on a guess.

## How alike, exactly: CockroachDB vs PostgreSQL 13

An earlier draft of this file claimed CockroachDB was indistinguishable from
PostgreSQL through `DatabaseMetaData`. That claim generalised from four measured
fields to the whole interface, and it is wrong. Measured properly — CockroachDB
v24.1 against `postgres:13`, the version Cockroach claims, across all 135 no-arg
scalar `DatabaseMetaData` methods — **122 are identical and 13 differ.**

Two of the 13 are environmental (`getURL`, `getUserName`) and two are ordinary
version differences. Nine are genuine identity signals:

| Method | PostgreSQL 13 | CockroachDB 24.1 |
|---|---|---|
| `getSQLKeywords()` | Postgres keyword list | contains `changefeed`, `backup`, `kv`, `nonvoters`, `virtual_cluster` |
| `getDefaultTransactionIsolation()` | `2` (READ_COMMITTED) | `8` (SERIALIZABLE) |
| `getMaxCatalogNameLength()` | `63` | `-2` |
| `getMaxColumnNameLength()` | `63` | `-2` |
| `getMaxCursorNameLength()` | `63` | `-2` |
| `getMaxProcedureNameLength()` | `63` | `-2` |
| `getMaxSchemaNameLength()` | `63` | `-2` |
| `getMaxTableNameLength()` | `63` | `-2` |
| `getMaxUserNameLength()` | `63` | `-2` |

### Why accent still queries `SELECT version()`

Not because nothing else distinguishes them, but because nothing else
distinguishes them *well*.

**None of these is a free local read.** pgjdbc implements `getSQLKeywords()`,
the `getMax*NameLength()` family and `getDefaultTransactionIsolation()` by
querying the server — `pg_catalog.pg_get_keywords()`, `pg_catalog.pg_settings`.
They are round trips with the query hidden inside the driver. So
`of(DatabaseMetaData)` was never query-free, and choosing `SELECT version()`
does not introduce I/O that these alternatives avoid. It only makes the I/O
explicit.

**Each is a weaker signal.**

- `getDefaultTransactionIsolation()` is unusable. `default_transaction_isolation`
  is a configurable Postgres setting, so a PostgreSQL server configured for
  serializable reports `8` as well. That is a false positive on the exact
  database accent must not misidentify.
- `-2` from the `getMax*NameLength()` family means "unknown", not "CockroachDB".
  Detection resting on an engine failing to answer breaks silently the day the
  engine starts answering.
- `getSQLKeywords()` content is the strongest of the three — `changefeed` is
  distinctly CockroachDB — but the list is large and shifts between releases,
  so pinning a heuristic to it means re-verifying on every Cockroach upgrade.

`SELECT version()` is one explicit round trip returning a documented string that
names the product outright. It is cheaper than `getSQLKeywords()`, stable across
releases in a way a keyword list is not, and it identifies every member of the
family rather than just one. That is why it is the seam.

## SKIP LOCKED, by contention

Every table above answered "does this statement execute" — a parse check. It is
a weaker test than it looks: every current server in the matrix except HSQLDB,
SQLite and Derby accepts `FOR UPDATE SKIP LOCKED` as syntax, including at least
one engine that turns out not to honour it. The property a caller actually
depends on is that a second transaction *skips* a row a first transaction has
locked, rather than blocking on it. That takes two connections holding
contention against each other, not one connection running a statement alone.

The harness (`SkipLockedContention.skipsLockedRows`, exercised by
`SkipLockedContentionIT` and `SkipLockedContentionHeavyIT`): connection one
locks row 1 with plain `FOR UPDATE` and holds it in an open transaction;
connection two runs `SELECT ... ORDER BY id FOR UPDATE SKIP LOCKED` against
both rows with a 5-second query timeout. Genuine skip-locked semantics return
row 2 only. Anything else — both rows (clause accepted and ignored), a timeout
(blocked), or an exception (plain `FOR UPDATE` itself rejected) — is recorded
as "does not skip."

| Engine | Version tested | Accepted the syntax (parse-only, above) | Skips by contention | `supportsSkipLocked()` |
|---|---|---|---|---|
| PostgreSQL | 17 | accepted | **skips** | `true` (floor: 9.5) |
| MySQL | 8.4 | accepted | **skips** | `true` (floor: 8.0) |
| MariaDB | 11.4 | accepted | **skips** | `true` (floor: 10.6) |
| CockroachDB | 24.1 | accepted | **skips** | `true` (floor: 22.2 of its own version — see below) |
| YugabyteDB | 2024.1 | accepted | **skips** | `true` (floor: 2.16 of its own version — see below) |
| H2 | 2.3.232 | accepted | **skips** | `true` (floor: 2.2 — see below) |
| Oracle | 23.26 | accepted | **skips** | `true` (floor: 11 — lowest version measured, see below) |
| Db2 | 12.1 | accepted | **does not skip — accepts and ignores the clause** | `false` |
| SQL Server | 2022 | rejects `FOR UPDATE`/`FOR UPDATE SKIP LOCKED` outright (uses `WITH (UPDLOCK, READPAST)` instead) | does not skip | `false` |
| HSQLDB | 2.7 | rejected | does not skip | `false` |
| SQLite | 3.47 | rejected (no row-locking model; plain `FOR UPDATE` is itself a syntax error) | does not skip | `false` |
| Derby | 10.17 | rejected | does not skip | `false` |

### Where contention disagrees with the parse-only result — the important finding

**Db2 is the disagreement that matters, and it is the strongest argument in
this document for why a parse check is insufficient.** The "SKIP LOCKED"
table under "Heavy images" above records Db2 12.1 accepting `FOR UPDATE SKIP
LOCKED` with no error, and that acceptance is real — the statement executes.
The naive conclusion from "accepted" is "supports skip locked." A
two-connection contention test shows the truth is different and more
dangerous: the second connection does not block, and it does not error. It
returns **both rows**, including the one the first connection still holds
locked in an open transaction:

```
did not skip: returned [1, 2] — clause accepted and ignored
```

Db2 parses the clause and silently ignores its semantics — a parse check can
never distinguish that outcome from genuine support, because both return
"accepted" for the exact same reason: the statement is syntactically valid.
Only holding a lock and watching a second connection's actual rows does. This
is a worse trap than blocking would have been: a caller doing outbox claiming
against Db2 on the strength of the syntax being accepted would hand the same
row to two workers at once, silently, under concurrency — exactly the failure
mode this predicate, and this library, exist to prevent. This is exactly the
trap `SPEC.md` §4.3 and this file's own earlier "parsing is not semantics"
warning predicted in the abstract — Db2 is where it turned out to be
concretely true, and worse than the abstract warning implied.

**CockroachDB and YugabyteDB confirm rather than contradict** the parse-only
result, but that agreement was not guaranteed going in — `SPEC.md`'s original
sketch (§1) guessed CockroachDB's semantics differ enough from PostgreSQL's
that a caller should fall back to plain `FOR UPDATE` for it. Contention shows
otherwise: both genuinely skip. Their `Platform` arms gate `supportsSkipLocked()`
on a version floor of their own engine's version, not the impersonated
`version()` PostgreSQL reports (13.0 and 11.2 respectively — see the tables
above), because there is no meaningful major/minor of the real engine in
`version()` to gate on. CockroachDB's floor (22.2) is a discovered boundary;
YugabyteDB's (2.16) is only the lowest version measured. See "SKIP LOCKED,
below the floor" below for both.

**H2 also confirms rather than contradicts** the parse-only result recorded
above (which itself already overturned `SPEC.md` §4.3's guess that H2 would
reject the clause). Contention settles the remaining question the parse-only
result left open: yes, H2 2.3.232 genuinely skips — but so do all versions
back to 2.2.224, and 2.1.214 and earlier genuinely do not. H2's arm gates on
a floor of 2.2, a discovered boundary found by running a classpath matrix of
five H2 versions rather than a container series. See "H2's floor is also a
discovered boundary" below.

**SQL Server confirms the parse-only table's implicit answer.** It never
appeared as "accepted" for `FOR UPDATE SKIP LOCKED` in the first place — its
row above uses the different `WITH (UPDLOCK, READPAST)` syntax. Contention
against the exact `FOR UPDATE SKIP LOCKED` clause fails immediately (plain
`FOR UPDATE` outside a cursor is rejected: `FOR UPDATE clause allowed only for
DECLARE CURSOR`), confirming `supportsSkipLocked()` must stay `false`. Do not
"fix" this arm to `true` on the strength of `READPAST` — it is a different
statement with different semantics, outside what this predicate promises to
cover.

## SKIP LOCKED, below the floor — the version-series contention table

The tables above establish that current-version PostgreSQL, MySQL, MariaDB,
and CockroachDB genuinely skip. They do not establish *where* skipping starts.
A second contention run, across a version series for the four arms with a
gated floor, does:

| Engine | Version | Accepted the syntax | Skips by contention | `supportsSkipLocked()` |
|---|---|---|---|---|
| PostgreSQL | 9.4.26 | rejected | **does not skip** — `syntax error at or near "SKIP"` | `false` |
| PostgreSQL | 9.5+ | accepted | skips | `true` (floor: 9.5) |
| MySQL | 5.7.44 | rejected | **does not skip** — SQL syntax error near `'SKIP LOCKED'` | `false` |
| MySQL | 8.0+ | accepted | skips | `true` (floor: 8.0) |
| MariaDB | 10.5.29 | rejected | **does not skip** — SQL syntax error near `'SKIP'` | `false` |
| MariaDB | 10.6+ | accepted | skips | `true` (floor: 10.6) |
| CockroachDB | v22.1.22 | accepted | **does not skip** — `ERROR: unimplemented: SKIP LOCKED lock wait policy is not supported` | `false` |
| CockroachDB | v22.2.19 | accepted | skips | `true` |
| CockroachDB | v23.1.30 | accepted | skips | `true` |
| CockroachDB | v24.1.32 | accepted | skips | `true` |

Every below-floor row above is exercised by
`SkipLockedContentionFloorsIT` (tagged `floors`, excluded from a default `mvn
verify`; run with `-Dexcluded.test.groups=`).

### CockroachDB's floor is a discovered boundary — and inexpressible from `Version`

PostgreSQL, MySQL, and MariaDB's floors were already known arrival versions;
the below-floor row above just confirms them by contention rather than by
documentation. CockroachDB's floor is different: it was *found*, not looked
up, and it cannot be expressed from `Platform.Version` at all. CockroachDB
reports `productVersion` = `13.0.0` at v22.1.22, v22.2.19, v23.1.30, *and*
v24.1.32 — the same bare PostgreSQL compatibility number at every one of
those releases. There is no major or minor in `Version` that separates a
version that skips from one that doesn't.

The only place CockroachDB's own version appears is the `SELECT version()`
string detection already fetches and, before this table existed, discarded:

```
CockroachDB CCL v22.1.22 (x86_64-pc-linux-gnu, built 2023/08/14 14:43:28, go1.17.11)
CockroachDB CCL v24.1.32 (aarch64-unknown-linux-gnu, built 2026/07/22 12:34:17, go1.22.12 X:nocoverageredesign)
```

`Platform.CockroachDB` now carries a second component, `engine`, an
`EngineVersion` parsed from that string (`v(\d+)\.(\d+)`) — `raw`, `major`,
`minor`. `supportsSkipLocked()` gates on `engine`, not on `version()`: true
when major > 22, or major == 22 and minor >= 2. `engine().raw()` is also the
only way a caller can learn CockroachDB's real version at all, since
`version()` never will.

### H2's floor is also a discovered boundary — found on a classpath matrix, not a container series

H2 is not containerisable, so it cannot be measured the way the four rows
above were: there is no way to run two different H2 *versions* against each
other or side by side in separate containers. Instead, each version's jar was
put on the test classpath in turn and run against the same contention
harness, one version at a time:

| H2 version | Accepted the syntax | Skips by contention | `supportsSkipLocked()` |
|---|---|---|---|
| 1.4.200 | rejected | **does not skip** — `Syntax error in SQL statement "... FOR UPDATE SKIP[*] LOCKED"` | `false` |
| 2.0.206 | rejected | **does not skip** — same syntax error | `false` |
| 2.1.214 | rejected | **does not skip** — same syntax error | `false` |
| 2.2.224 | accepted | skips | `true` (floor: 2.2) |
| 2.3.232 | accepted | skips | `true` (floor: 2.2) |

The floor is **2.2**, not 2.3: 2.2.224 genuinely skips, so rounding the floor
up to 2.3 would wrongly deny the capability to every H2 2.2.x release. H2
reports its own version honestly through `DatabaseMetaData` (see the
"Embedded engines" table at the top of this file), so
`H2#supportsSkipLocked()` gates directly on `majorVersion()`/
`minorVersion()` — true when major > 2, or major == 2 and minor >= 2 — with
no `EngineVersion` component needed, unlike CockroachDB or YugabyteDB.

### YugabyteDB has the same shape, but 2.16 is a measured floor, not a discovered one

YugabyteDB reports the same kind of impersonated version —
`11.2-YB-2.16.9.0-b0` — and gets the same `EngineVersion` treatment, parsed
with `-YB-(\d+)\.(\d+)`. But do not read its floor the way CockroachDB's
reads. 2.16.9, 2.18.9, 2.20.12, and 2024.1 (parsing as major 2024, minor 1)
all genuinely skip; 2.14.17 would not start on the test machine, so nothing
below 2.16.9 was ever measured. **2.16 is the lowest version measured, not a
confirmed line between "works" and "does not work."** `YugabyteDB` gates
`supportsSkipLocked()` on the same major/minor comparison as CockroachDB
(true when major > 2, or major == 2 and minor >= 16), but the javadoc on
`Platform.YugabyteDB` is explicit that this is a floor of convenience, not a
boundary anyone has proven.

### An unparseable `EngineVersion` is not evidence of anything

If `SELECT version()` ever returns a CockroachDB or YugabyteDB string this
regex cannot parse, `EngineVersion` is still constructed — with `raw` holding
the unparsed text and `major`/`minor` both `0` — rather than the whole of
detection failing. Detection succeeded: the platform is known. But an
unparseable version is no evidence of capability, so `supportsSkipLocked()`
answers `false` in that case, the same safe default as every other
below-floor reading.
