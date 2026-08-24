# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
