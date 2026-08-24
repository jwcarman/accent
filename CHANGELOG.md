# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-08-24

First release.

### Added

- `Accent.of(DataSource | Connection | DatabaseMetaData)` identifies which
  database a JDBC connection is talking to, returning a sealed `Platform` with
  thirteen arms: `PostgreSQL`, `CockroachDB`, `YugabyteDB`, `MySQL`, `MariaDB`,
  `Oracle`, `SqlServer`, `Db2`, `H2`, `HSQLDB`, `SQLite`, `Derby`, and
  `Unknown`. Because the interface is sealed, a `switch` over it is exhaustive
  without a `default`, so the compiler forces every caller to decide what
  happens for a database accent has not been taught.
- Detection distinguishes engines that report themselves as something else.
  CockroachDB and YugabyteDB both report a product name of `PostgreSQL`, and
  MariaDB reached through `mysql-connector-j` reports `MySQL`. All three are
  identified correctly, verified against live servers.
- `SELECT version()` is issued for the PostgreSQL family only. CockroachDB
  reports `productVersion` of `13.0.0` at every release, so no
  `DatabaseMetaData` field distinguishes it from genuine PostgreSQL 13; no
  other family pays for the round trip.
- `Platform.CockroachDB` and `Platform.YugabyteDB` carry an `engine()`
  component of type `Platform.EngineVersion`, parsed from `SELECT version()`.
  It is the only way a JDBC caller can learn the real CockroachDB version,
  since the driver reports the emulated PostgreSQL release instead.
- `Platform.supportsSkipLocked()` reports whether `FOR UPDATE SKIP LOCKED` has
  genuine skip-locked semantics — a second transaction skipping rows a first
  has locked rather than blocking on them. Every arm's answer comes from a
  two-connection contention test against a live server, never from a syntax
  check: every current engine except HSQLDB, SQLite, and Derby *parses* the
  clause, including Db2, which then ignores it and returns rows another
  connection holds locked.
- Version floors for `supportsSkipLocked()`, each measured. Discovered
  boundaries, with a real below-floor failure on record: PostgreSQL 9.5,
  MySQL 8.0, MariaDB 10.6, CockroachDB 22.2, H2 2.2. Bounded by measurement
  with no boundary found, because no older image is published: Oracle 11,
  YugabyteDB 2.16. `SqlServer`, `Db2`, `HSQLDB`, `SQLite`, and `Derby` report
  `false`, also measured.
- `Accent.builder().fallback(...)` supplies a caller's own identification when
  detection would otherwise return `Unknown` — for a database behind a
  connection pooler, a managed service, or a compatibility mode.
- `AccentException` for failures to read the database. `Unknown` means "asked,
  did not recognise" and never "could not ask"; the two facts are kept
  distinct.
- Zero runtime dependencies. Plain JDBC only.

### Requirements

- Java 25.

[Unreleased]: https://github.com/jwcarman/accent/compare/0.1.0...HEAD
[0.1.0]: https://github.com/jwcarman/accent/releases/tag/0.1.0
