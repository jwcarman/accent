# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- `H2#supportsSkipLocked()` no longer returns `true` unconditionally. A
  classpath matrix across five H2 versions (H2 is not containerisable, so
  each version's jar was run in turn against the same contention harness)
  found 1.4.200, 2.0.206, and 2.1.214 all genuinely reject `FOR UPDATE SKIP
  LOCKED` with a syntax error, while 2.2.224 and 2.3.232 genuinely skip. This
  is a discovered boundary, like CockroachDB's. `H2#supportsSkipLocked()` now
  gates on `majorVersion()`/`minorVersion()` directly — H2 reports its own
  version honestly, so unlike CockroachDB or YugabyteDB no separate
  `engine()` component is needed. See
  [`docs/capabilities.md`](docs/capabilities.md).
- `Platform.Oracle`'s class javadoc no longer claims Oracle's product version
  "spans two lines" unconditionally. Measured across Oracle 11.2.0.2, 18.4.0,
  21.3.0, and 23.26, only 18c and later actually span two lines; 11g XE
  reports a single line. The javadoc now says so, and warns that a heuristic
  anchored with `$` or requiring a newline breaks on one shape or the other —
  accent is unaffected because it uses the integer accessors.
- `Platform.Oracle#supportsSkipLocked()`'s javadoc no longer cites Oracle's
  documentation for the floor's arrival version alongside a contention
  measurement of a different version, which read as more verified than it
  was — the same shape of claim that turned out to be wrong for CockroachDB.
  Reworded in the same style as the `YugabyteDB` javadoc: 11 is the lowest
  version measured (11.2.0.2, 18.4.0, 21.3.0, and 23.26 all genuinely skip; no
  Oracle 10g image is published, so nothing below 11.2.0.2 could be tested),
  not a discovered boundary. Also documents that Oracle 18c reports
  `getDatabaseMinorVersion()` as `0` despite being release 18.4, so a future
  minor-version comparison on this floor would silently misbehave. No
  behaviour change.

- `CockroachDB#supportsSkipLocked()` and `YugabyteDB#supportsSkipLocked()` no
  longer return `true` unconditionally.
  Contention testing across a version series found CockroachDB v22.1.22
  genuinely does not skip locked rows (`ERROR: unimplemented: SKIP LOCKED
  lock wait policy is not supported`), while v22.2.19 and above genuinely do.
  `Platform.CockroachDB` and `Platform.YugabyteDB` now carry a second
  component, `engine()` (an `EngineVersion` of `raw`/`major`/`minor`), parsed
  from the `SELECT version()` string detection already fetches, because
  `version()` cannot express this floor — CockroachDB reports `productVersion`
  = `13.0.0` at every version in the series regardless of whether it skips.
  `supportsSkipLocked()` now gates on `engine()`: `true` above CockroachDB
  22.2 and YugabyteDB 2.16. YugabyteDB's floor is the lowest version measured,
  not a discovered boundary like CockroachDB's — see
  [`docs/capabilities.md`](docs/capabilities.md). An unparseable `engine()`
  answers `false`, never a guess.

### Added

- Initial release: `Accent.of(DataSource | Connection | DatabaseMetaData)`
  identifies which database a JDBC connection is talking to, returning a
  sealed `Platform` with thirteen arms — `PostgreSQL`, `CockroachDB`,
  `YugabyteDB`, `MySQL`, `MariaDB`, `Oracle`, `SqlServer`, `H2`, `HSQLDB`,
  `SQLite`, `Derby`, `Db2`, and `Unknown`.
- `Platform.supportsSkipLocked()` — whether `FOR UPDATE SKIP LOCKED` has
  genuine skip-locked semantics on this platform, verified by a two-connection
  contention test rather than a syntax-acceptance check.
- `Accent.builder().fallback(...)` — a caller-supplied identification for
  databases accent does not recognise, consulted only when detection would
  otherwise return `Unknown`.
- Detection reads `DatabaseMetaData` and, for the PostgreSQL family only,
  issues `SELECT version()` to distinguish CockroachDB and YugabyteDB from
  genuine PostgreSQL.
- Zero runtime dependencies.
