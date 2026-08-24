# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- `CockroachDB#supportsSkipLocked()` no longer returns `true` unconditionally.
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
