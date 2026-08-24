# Getting Started

## Add the dependency

accent is **not yet published to Maven Central** — `pom.xml` on `main` carries
`0.1.0-SNAPSHOT`, and there is no `0.1.0` artifact to resolve today. Once a
release ships, the coordinates will be:

```xml
<dependency>
    <groupId>org.jwcarman.accent</groupId>
    <artifactId>accent</artifactId>
    <version>0.1.0</version>
</dependency>
```

accent has zero runtime dependencies — plain JDBC only — so adding it pulls in
nothing else.

## Make the first call

`Accent` has three entry points, one per JDBC type you're likely to be holding:

```java
Platform platform = Accent.of(dataSource);   // opens a Connection, closes it
Platform platform = Accent.of(connection);   // does NOT close it
Platform platform = Accent.of(metaData);     // the testable seam
```

`of(DataSource)` is the common case: it opens a connection, reads what it
needs, and closes the connection before returning. `of(Connection)` is for
code that already holds one open and doesn't want accent closing it.
`of(DatabaseMetaData)` is the narrowest seam — the one accent's own unit tests
drive, since `DatabaseMetaData` can be mocked with no database running.

Detection reads `DatabaseMetaData` (`getDatabaseProductName()`,
`getDatabaseProductVersion()`, the major/minor accessors). For the PostgreSQL
family only — because CockroachDB and YugabyteDB both report a product name
of `PostgreSQL` — it also issues `SELECT version()` over the connection,
reached via `DatabaseMetaData#getConnection()` when you call `of(metaData)`
directly. No other family pays for that round trip. See
[Design](design.md#why-select-version) for why.

## Read the result

`Platform` is a sealed interface with thirteen arms plus `Unknown`. Most
callers never need to look past one predicate:

```java
Platform platform = Accent.of(dataSource);

String claimSql = platform.supportsSkipLocked()
    ? "SELECT ... FOR UPDATE SKIP LOCKED"
    : "SELECT ... FOR UPDATE";
```

`supportsSkipLocked()` answers one narrow, contention-tested question — see
[Capabilities](capabilities.md). Every `Platform` also exposes the raw
identity the driver reported:

```java
platform.productName();     // e.g. "PostgreSQL", exactly as the driver said it
platform.productVersion();  // e.g. "17.10 (Debian 17.10-1.pgdg13+1)"
platform.majorVersion();    // 17
platform.minorVersion();    // 10
```

For platforms that impersonate another engine — `CockroachDB` and
`YugabyteDB` — these four values describe the *impersonation*, not the real
engine. See [Platforms](platforms.md) before you write version-comparison
logic against one of those two arms.

## Handle what accent doesn't recognise

If detection can't place the database, `Accent.of(...)` returns
`Platform.Unknown` rather than throwing. A `switch` over `Platform` must
still handle it — that's the point of a sealed type with no `default` arm.
See [Unknown](unknown.md) for why `Unknown` exists and how to supply your own
fallback for things like pgbouncer or Aurora.

A connection failure is a different fact from "asked, didn't recognise it,"
and is never reported as `Unknown`: `Accent.of(...)` throws an unchecked
`AccentException` if the database can't be reached or read.

## Next steps

- [Platforms](platforms.md) for the full arm-by-arm reference.
- [Capabilities](capabilities.md) for what `supportsSkipLocked()` actually
  verifies.
- [Design](design.md) for why the API looks the way it does.
