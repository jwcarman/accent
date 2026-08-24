# accent — specification

**Status:** implemented and released (0.1.0). This document is the original
seed spec, preserved as the record of what was assumed before anything was
measured — several of its guesses were wrong. See
`docs/superpowers/specs/2026-08-24-accent-design.md` for the decisions that
superseded it, and `docs/observed-strings.md` for what measurement actually
found.
**Coordinates:** `org.jwcarman.accent:accent`
**Java:** 25
**License:** Apache 2.0, © 2026 James Carman

> Every one of these databases speaks SQL. They differ by *accent*.

---

## 1. What it is

A tiny, dependency-free library that answers one question about a JDBC
`DataSource` or `Connection`:

> **Which database am I actually talking to?**

It answers with a **sealed vocabulary** so callers can `switch` over the result
and have the compiler force them to handle every case.

```java
Platform platform = Accent.of(dataSource);

String claimSql = switch (platform) {
    case PostgreSQL p   -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case CockroachDB c  -> "SELECT ... FOR UPDATE";            // NOT the same semantics
    case MySQL m        -> m.majorVersion() >= 8
                             ? "SELECT ... FOR UPDATE SKIP LOCKED"
                             : "SELECT ... FOR UPDATE";
    case SqlServer s    -> "SELECT ... WITH (READPAST, UPDLOCK)";
    case Unknown u      -> throw new UnsupportedOperationException(u.productName());
    // ... every other arm
};
```

> **This was the pre-implementation guess.** The `CockroachDB` line above is
> wrong: contention testing found CockroachDB genuinely honours
> `FOR UPDATE SKIP LOCKED`, and `Platform.CockroachDB#supportsSkipLocked()`
> returns `true`. This example is left as originally written — §5 already
> flags its tables as hypotheses, not measurements — but do not copy it. See
> [`docs/observed-strings.md`](docs/observed-strings.md) for what was actually
> measured, and the README for the corrected example.

## 2. Why it needs to exist

`DatabaseMetaData.getDatabaseProductName()` is five lines away and *insufficient*.
The whole value of this library is the curation on top of it.

**Postgres-compatible engines lie.** CockroachDB, Amazon Redshift, and
YugabyteDB all answer `"PostgreSQL"` when reached through the standard pgjdbc
driver. They speak the wire protocol and satisfy the metadata call identically —
while differing on exactly the things callers branch on. CockroachDB's
`SELECT ... FOR UPDATE` does not mean what Postgres's means, and Redshift has no
row-level locking worth the name. Anyone who trusts the product name alone ships
a bug that only appears under concurrency, on one deployment topology.

**Drivers disagree about the same server.** MariaDB reached through
`mysql-connector-j` reports `"MySQL"`; reached through `mariadb-java-client` it
reports `"MariaDB"`. The same server, two identities, depending on a dependency
choice made elsewhere.

**Everyone re-solves this badly.** The heuristics live, undocumented and
internal, inside Flyway, Liquibase, Hibernate, and Spring Boot. Nessy already
rolls her own; Continuum will need one. Two consumers is the usual signal that
the thing should be a library.

## 3. Design decisions already made

These were settled in discussion. Re-open them only with a reason.

### 3.1 Sealed, not enum, not open interface

The prior art splits cleanly on one question: *may a third party add a platform?*

| Library | Shape | Why |
|---|---|---|
| jOOQ `SQLDialect`, Spring Boot `DatabaseDriver` | enum | sole curator, ships often |
| Flyway `DatabaseType`, Liquibase `Database` | open interface + `ServiceLoader` | third-party extension is a feature |

**accent is the sole curator, so sealed wins.** It beats an enum because records
can carry per-platform data (version, raw product string) and pattern matching
extracts it at the branch. It beats an open interface because exhaustiveness at
compile time *is the product*.

### 3.2 `Unknown` is the default branch

This is the load-bearing idea. A permanent

```java
record Unknown(String productName, String productVersion, int majorVersion, int minorVersion)
```

arm means a `switch` stays exhaustive **without a `default`**, which forces every
caller to decide, at compile time, what happens for a database accent hasn't been
taught. The failure mode changes from "silently fell into a default that guessed
wrong" to "you wrote the unknown case deliberately."

`Unknown` must carry the raw strings so a caller can log something actionable and
so users can report gaps.

### 3.3 The known compatibility cost, accepted

Adding a permitted subtype later throws `MatchException` in **already-compiled**
exhaustive switches (the compiler inserts an implicit throwing default). So
adding `Db2` in 0.3.0 is a binary-breaking change for anyone who switched
exhaustively against 0.2.0 and did not recompile.

This is accepted, because the exhaustive switch *is* the value proposition.
Mitigate it:

- Document prominently: *include a `default` unless you pin the version.*
- Make exhaustive switching **opt-in, not necessary** — see §4.3. Most callers
  want one capability answer, not a fourteen-arm switch.
- Treat adding a platform as a **minor** bump pre-1.0, and decide before 1.0
  whether the vocabulary is frozen or whether additions justify majors.

### 3.4 Postgres-compatible engines get their own arms

Not a `PostgresCompatible` catch-all. `CockroachDB` and `Redshift` are distinct
arms, so a caller who must treat them differently is *forced* to notice them, and
a caller who genuinely wants "anything Postgres-shaped" can group arms in one
`case` label. Grouping is easy; un-conflating is not.

## 4. API sketch

Package: `org.jwcarman.accent`. This is a sketch, not a mandate — improve it.

### 4.1 Entry point

```java
public final class Accent {
    public static Platform of(DataSource dataSource);      // opens + closes a Connection
    public static Platform of(Connection connection);      // does NOT close
    public static Platform of(DatabaseMetaData metaData);  // the testable seam
}
```

`of(DatabaseMetaData)` is what the unit tests drive — every heuristic must be
testable by mocking metadata, with no database running.

Failure policy: a `SQLException` should surface as an unchecked
`AccentException` carrying the cause. Never return `Unknown` to paper over a
connection failure — "could not ask" and "asked, did not recognise" are
different facts and must not collapse.

### 4.2 The vocabulary

```java
public sealed interface Platform {
    String productName();      // raw, exactly as the driver reported it
    String productVersion();   // raw
    int majorVersion();
    int minorVersion();

    record PostgreSQL(...)  implements Platform {}
    record CockroachDB(...) implements Platform {}
    record Redshift(...)    implements Platform {}
    record YugabyteDB(...)  implements Platform {}
    record MySQL(...)       implements Platform {}
    record MariaDB(...)     implements Platform {}
    record Oracle(...)      implements Platform {}
    record SqlServer(...)   implements Platform {}
    record H2(...)          implements Platform {}
    record HSQLDB(...)      implements Platform {}
    record SQLite(...)      implements Platform {}
    record Derby(...)       implements Platform {}
    record Db2(...)         implements Platform {}
    record Unknown(...)     implements Platform {}
}
```

Consider whether the four common components belong in a single
`record Version(String productName, String productVersion, int major, int minor)`
component rather than repeated on every arm. Repetition across fourteen records
is a real maintenance cost; a nested value type keeps `Platform` accessors as
default methods delegating to it. **Decide this before writing all fourteen.**

### 4.3 Capabilities — make switching optional

Most callers do not want a fourteen-arm switch; they want one answer. Provide it,
so the sealed hierarchy is for the minority who genuinely need per-platform SQL:

```java
boolean supportsSkipLocked();   // Postgres 9.5+, MySQL 8+, Oracle, MariaDB 10.6+; NOT Redshift/H2-in-some-modes
```

Keep this surface **small and defensible**. Every predicate is a promise to be
correct on every platform forever. Start with `supportsSkipLocked()` because it
is the concrete need driving Continuum, and add only on demand. Resist a general
"capabilities" bag.

## 5. Detection heuristics

Product name first, then version-string disambiguation.

> ### ⚠ Everything in the table below is a GUESS
>
> These strings are written from memory and have **not** been verified against a
> running database. Several are probably subtly wrong — a different suffix, a
> different capitalisation, a disambiguator that does not actually appear in the
> version string. **Do not implement against this table.** Implement against
> §6: stand the containers up, print what the drivers actually report, and let
> the observed strings define the heuristics. The table is a starting hypothesis
> and a list of cases to cover, nothing more.

| Engine | `getDatabaseProductName()` (hypothesis) | Disambiguator (hypothesis) |
|---|---|---|
| PostgreSQL | `PostgreSQL` | none — the fallback after ruling out the impostors |
| CockroachDB | `PostgreSQL` (pgjdbc) | version string contains `CockroachDB` |
| Redshift | `PostgreSQL` (pgjdbc) / `Redshift` (AWS driver) | version string; pgjdbc reports a very old PG version (~8.0.2) |
| YugabyteDB | `PostgreSQL` (pgjdbc) | version string |
| MySQL | `MySQL` | none, after ruling out MariaDB |
| MariaDB | `MariaDB` (mariadb-java-client) / `MySQL` (mysql-connector-j) | version string contains `MariaDB` |
| Oracle | `Oracle` | |
| SQL Server | `Microsoft SQL Server` | |
| H2 | `H2` | |
| HSQLDB | `HSQL Database Engine` | |
| SQLite | `SQLite` | |
| Derby | `Apache Derby` | |
| Db2 | `DB2/LINUXX8664`, `DB2/NT`, … | prefix match on `DB2` — suffix is platform-specific |

Matching must be **case-insensitive**, and must use prefix or contains checks
where that is what the driver actually guarantees — not exact equality.

## 6. Testing — Testcontainers is the source of truth

This is the part that makes the library trustworthy. Without it, accent is just
someone's guesses with a sealed type wrapped around them.

### 6.1 The matrix is driver × server, not server

The impostor cases — the entire reason this library exists — only reproduce with
a specific *pairing*. Testing "MariaDB" once proves nothing; the whole point is
that the same server reports differently through different drivers.

| # | Server (image) | Driver | Must detect | Verifies |
|---|---|---|---|---|
| 1 | `postgres` | pgjdbc | `PostgreSQL` | baseline |
| 2 | `cockroachdb/cockroach` | **pgjdbc** | `CockroachDB` | reports `PostgreSQL` — must NOT be PostgreSQL |
| 3 | `yugabytedb/yugabyte` | **pgjdbc** | `YugabyteDB` | same impostor shape |
| 4 | `mysql` | mysql-connector-j | `MySQL` | baseline |
| 5 | `mariadb` | **mariadb-java-client** | `MariaDB` | native driver path |
| 6 | `mariadb` | **mysql-connector-j** | `MariaDB` | reports `MySQL` — must NOT be MySQL |
| 7 | `mcr.microsoft.com/mssql/server` | mssql-jdbc | `SqlServer` | EULA env var required |
| 8 | `gvenzl/oracle-free` | ojdbc | `Oracle` | slow image; consider a tagged group |
| 9 | `icr.io/db2_community/db2` | jcc | `Db2` | licence acceptance; suffix varies by host arch |

Rows **2, 3 and 6 are the ones that matter most**. If those three pass, the
library is doing something no product-name check does. Write them first.

### 6.2 Embedded engines need no container

H2, HSQLDB, SQLite, and Derby are in-process. Add the driver test-scope, open an
in-memory URL, assert. These are cheap enough to live in the **fast** suite and
should be exhaustive rather than sampled.

### 6.3 Redshift cannot be containerised

There is no Redshift image; it is AWS-only. So a `Redshift` arm in 0.1.0 would be
the one platform shipping on an unverifiable guess, inside a hierarchy whose
selling point is compile-time certainty. **Recommendation: do not ship a
`Redshift` arm until someone can verify it against a real cluster.** Let it fall
into `Unknown`, which is exactly what `Unknown` is for. Adding it later is a
breaking change (§3.3) — that is the honest cost of not guessing, and it is
cheaper than a wrong arm.

Apply the same rule to any arm nobody can stand up: **an unverified arm is worse
than no arm**, because the sealed type invites callers to trust it.

### 6.4 Capture the raw strings as fixtures

Have the integration tests **print and assert** the exact `productName` /
`productVersion` observed for each pairing, and mirror those exact strings into
the unit-test fixtures. That gives two layers:

- **Unit tests** (fast, no Docker) pin the heuristics against known-real strings.
- **Integration tests** (slow, `*IT` under failsafe) detect the day a driver or
  server upgrade changes what it reports.

When an IT fails after a version bump, the observed string has changed and both
the heuristic and the unit fixture need updating together. That is the intended
maintenance loop, and it is worth a paragraph in the README.

### 6.5 CI shape

Containers make the full matrix slow. Keep `mvn verify` runnable locally without
every image: embedded engines and unit tests always run; heavyweight images
(Oracle, Db2) behind a profile or JUnit tag. CI runs everything. Document how to
run the full matrix locally, because a contributor adding a platform must be able
to.

Target 100% coverage of the detection logic. It is a pure function of two strings
and two ints; there is no excuse.

## 7. Non-goals

- **Not a dialect/SQL-generation library.** It reports identity and a minimal
  capability answer. It does not build SQL. jOOQ exists.
- **Not a migration tool.** Flyway and Liquibase exist.
- **No third-party extension point.** Sealed by choice (§3.1). A gap is a pull
  request or an issue, not a `ServiceLoader`.
- **No dependencies.** Plain JDBC only. Not Spring, not jOOQ, not Hibernate.
  Test-scope dependencies are fine.

## 8. Project scaffolding

Mirror `~/IdeaProjects/continuum` — same author, same conventions. Copy rather
than reinvent:

- **Parent pom** with `maven.compiler.release` 25, UTF-8, and the
  `<developers>`/`<licenses>`/`<scm>` blocks pointed at the accent repo.
- **Spotless** `google-java-format` GOOGLE style, bound to `validate` as `check`.
  Run `mvn spotless:apply` before every commit.
- **Profiles**: `release` (central-publishing-maven-plugin, source, javadoc, gpg),
  `ci` (jacoco prepare-agent + XML report), `license`
  (`com.mycila:license-maven-plugin`, Apache header, `${license.owner}`).
- **Sonar** properties: organization `jwcarman`, host `https://sonarcloud.io`,
  projectKey `jwcarman_accent`.
- **GitHub workflows**: copy `maven.yml`, `maven-publish.yml`, and `docs.yml`.
  Publishing triggers on `release: created` and runs `mvn versions:set` to strip
  `-SNAPSHOT`; **the pom version stays `-SNAPSHOT` in git at all times**.
- **Docs**: mkdocs-material, built with `--strict` in CI.
- **CHANGELOG.md** in Keep a Changelog format.
- **Version strings**: bare semver, **no `v` prefix anywhere** — tag, release
  title, and CHANGELOG heading are identical.

Single module is almost certainly right. Do not create a BOM for one artifact.

Javadoc must be clean under `mvn -P release javadoc:jar` with **zero warnings** —
continuum enforces this and it caught real bugs (doc comments placed after
annotations are silently ignored by javadoc; a `@throws` naming an unimported
type fails the build).

## 9. Open questions

1. **Version components repeated vs. nested** (§4.2) — decide before writing all
   fourteen records.
2. **How many arms in 0.1.0?** Every arm added later is a breaking change for
   exhaustive switchers, which argues for casting a wide net now. Against that:
   an arm you cannot test against a real database is a guess with a compile-time
   guarantee wrapped around it. **Resolution: ship exactly the arms §6 can
   verify** — container or embedded — let `Unknown` carry the rest, and treat 1.0
   as the freeze point. See §6.3: an unverified arm is worse than no arm.
3. **Is `supportsSkipLocked()` the right first capability, and does it belong on
   `Platform` at all** — or on a separate `Capabilities` type so `Platform` stays
   pure identity?
4. **Does version-aware detection need minor/patch?** `SKIP LOCKED` arrived in
   Postgres 9.5 and MariaDB 10.6, so major alone is insufficient.

## 10. First consumers

- **Continuum** (`~/IdeaProjects/continuum`) — `continuum-jdbc` is PostgreSQL-only
  today and wants multi-dialect support in a later release. Its
  `claimDeliveries` uses `FOR UPDATE SKIP LOCKED`; that is the dialect-sensitive
  part and the reason `supportsSkipLocked()` is the first capability. Note
  `continuum-jdbc` is deliberately Spring-free and dependency-light, so accent
  must stay dependency-free to be adoptable there.
- **Nessy** (`~/IdeaProjects/nessy`) — already rolls its own detection; a JDBC
  `Substrate` adapter is planned work.
