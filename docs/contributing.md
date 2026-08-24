# Contributing a platform

This page is what `SPEC.md` §6.5 requires: a contributor adding a platform
must be able to run the full matrix. Here's how.

## The rule that governs everything else

**An unverified arm is worse than no arm**, because the sealed `Platform`
type invites callers to trust every arm it has. Nothing in `src/main` is
written from memory or from a hypothesis table — it's written against
strings actually observed from a running database. This is why Redshift
isn't a `Platform` arm today (see [Design](design.md#why-redshift-is-absent))
and why capability claims are contention-tested rather than parse-checked
(see [Capabilities](capabilities.md)).

## Steps to add a platform

### 1. Get the observed strings

Stand the engine up — a Testcontainers image if one exists, an in-process
embedded engine otherwise — and print exactly what the driver reports:
`getDatabaseProductName()`, `getDatabaseProductVersion()`,
`getDatabaseMajorVersion()`, `getDatabaseMinorVersion()`. If the platform
might be confused with an existing arm (as CockroachDB and YugabyteDB are
with PostgreSQL, and MariaDB is with MySQL depending on driver), also try
every driver a caller plausibly uses against it — the impostor cases only
reproduce with a specific *pairing*, not a server alone.

Record what you observed in [Observed Strings](observed-strings.md), dated,
with the image tag and driver version, next to the existing rows. That file
is the source of truth the heuristics are written against — not `SPEC.md`
§5's hypothesis table, which was wrong about CockroachDB's version string and
about H2 rejecting `SKIP LOCKED`.

### 2. Add the arm

Add a `record` to `Platform` implementing the sealed interface, carrying a
`Version`:

```java
record NewEngine(Version version) implements Platform {}
```

Add the detection heuristic to `Detector` — a pure function of a
`Fingerprint`, no JDBC types, no I/O. Match case-insensitively, and use a
prefix or `contains` check instead of equality only where the driver actually
guarantees a varying suffix (as Db2's architecture suffix does) — equality
otherwise, since it's a stronger claim about what the driver returns and
avoids sweeping in unrelated products. If the new arm could be confused with
an existing one at the `DatabaseMetaData` level, resolve the ambiguity before
falling through to the arm it impersonates, the way CockroachDB and
YugabyteDB are resolved before `PostgreSQL`.

### 3. Add the unit fixture

Add the observed strings as constants in `ObservedStrings` and drive a
`DetectorTest` case against them — fast, no Docker. If the platform is an
impostor case, write the test that proves the *pairing* is detected
correctly, not just the server in isolation: that's the case that actually
justifies the arm existing.

### 4. Add the integration test

Add a `*IT` class under `src/test/java/org/jwcarman/accent/it`, run against a
live container (or, for embedded engines, `EmbeddedEngineTest`). It should
**print and assert** the exact `productName`/`productVersion` observed,
matching what you recorded in `docs/observed-strings.md` — that's what turns
a one-time observation into something CI re-checks on every driver or server
bump.

If the platform claims `supportsSkipLocked() == true`, don't assert that from
the hardcoded value — that's a tautology. Add or extend a contention test
(`SkipLockedContention.skipsLockedRows`) that holds a row lock on one
connection and asserts a second genuinely skips it, per
[Capabilities](capabilities.md#the-methodology). Heavyweight images (licence
gated or slow, like Oracle and Db2) go under a `*HeavyIT` variant excluded
from the default `mvn verify` run.

### 5. Run the matrix

```
mvn verify
```

runs unit tests, the embedded engines (H2, HSQLDB, SQLite, Derby), and the
container tests for PostgreSQL, CockroachDB, YugabyteDB, MySQL, MariaDB
(through both `mariadb-java-client` and `mysql-connector-j`), and SQL Server.

```
mvn verify -Dexcluded.test.groups=
```

adds Oracle and Db2, excluded from the default run because they're slow and
licence-gated. Db2 publishes no `linux/arm64` manifest, so on Apple Silicon
it runs only under amd64 emulation and is noticeably slow; CI runs it
natively on `linux/amd64` runners, where it takes about 70 seconds (Oracle
takes about 15). All heavyweight containers need Docker running locally. The
full matrix — 61 unit tests and 23 integration tests as of this writing,
Oracle and Db2 included — runs green on every pull request; CI does not
exclude any test group.

## The maintenance loop

Every heuristic accent has is pinned against strings actually observed from a
running database, recorded in `docs/observed-strings.md`, and mirrored into
unit fixtures that pin the heuristics without Docker. Integration tests
re-observe those same pairings against live containers on every CI run.

When a driver or server upgrade changes what it reports, the integration
test for that pairing fails first — the string it's asserting against has
changed underneath it. When that happens:

1. Confirm the change is real (re-run the IT, check driver/image changelogs)
   rather than a flaky container.
2. Update the heuristic in `Detector`/`Platform` **and** the corresponding
   row in `docs/observed-strings.md` together, in the same change. The
   fixture and the prose are two views of one fact — let them drift and the
   next contributor is back to guessing.
3. Re-run `mvn verify -Dexcluded.test.groups=` to confirm the fix against the
   full matrix, including the heavyweight images if the affected platform is
   Oracle or Db2.

## If your change touches this documentation site

The docs pipeline (`.github/workflows/docs.yml`) runs `mkdocs build --strict`
on every pull request that touches `docs/**` or `mkdocs.yml`, so a broken
internal link or a `nav` entry pointing at a file that doesn't exist fails
the build rather than silently shipping a broken page. It only *deploys*
from `main` — a pull request gets the strict build check but never touches
GitHub Pages. Update `mkdocs.yml`'s `nav` when you add or rename a page, and
keep process artifacts (specs, plans) under `docs/superpowers/`, which
`exclude_docs` keeps out of the published site.
