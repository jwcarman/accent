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
