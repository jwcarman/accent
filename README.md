# accent

Which database is this JDBC connection actually talking to?

> Every one of these databases speaks SQL. They differ by *accent*.

`DatabaseMetaData.getDatabaseProductName()` answers a related question badly.
CockroachDB and YugabyteDB both report `PostgreSQL` when reached through
pgjdbc — they speak the wire protocol and satisfy the metadata call
identically, while differing on exactly the things callers branch on
(`CockroachDB`'s `FOR UPDATE` does not mean what PostgreSQL's means). And
MariaDB reached through `mysql-connector-j` reports `MySQL`; reached through
`mariadb-java-client` it reports `MariaDB` — the same server, two identities,
depending on a dependency choice made somewhere else. accent curates these
impostors into a sealed vocabulary, so a caller can `switch` over the result
and let the compiler force every case to be handled:

```java
Platform platform = Accent.of(dataSource);

// Most callers stop here — one measured predicate, not a switch.
String claimSql = platform.supportsSkipLocked()
    ? "SELECT ... FOR UPDATE SKIP LOCKED"
    : "SELECT ... FOR UPDATE";
```

Callers who need the exact per-platform SQL — not just a yes/no answer — can
switch exhaustively instead. Grouping is a feature of the sealed design: every
arm that genuinely skips a locked row *unconditionally* shares one `case`
label. `PostgreSQL`, `MySQL`, `MariaDB`, `Oracle`, and `H2` only skip above a
version floor, so those arms consult `supportsSkipLocked()` in a guard rather
than being assumed — the same predicate the one-liner above already calls, so
the switch can never emit `SKIP LOCKED` against a version below the floor it
encodes:

```java
String claimSql = switch (platform) {
    // Genuinely skips regardless of version — see docs/capabilities.md.
    case CockroachDB _, YugabyteDB _
        -> "SELECT ... FOR UPDATE SKIP LOCKED";
    // Version-sensitive: consult the predicate that encodes the floor.
    case PostgreSQL p when p.supportsSkipLocked() -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case MySQL m when m.supportsSkipLocked() -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case MariaDB m when m.supportsSkipLocked() -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case Oracle o when o.supportsSkipLocked() -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case H2 h when h.supportsSkipLocked() -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case SqlServer s -> "SELECT ... WITH (UPDLOCK, READPAST)"; // a different statement, not a spelling
    case PostgreSQL _, MySQL _, MariaDB _, Oracle _, H2 _, Db2 _, HSQLDB _, SQLite _, Derby _, Unknown _
        -> "SELECT ... FOR UPDATE"; // safe fallback, including versions below the floor
};
```

Every arm that returns `true` from `supportsSkipLocked()` is backed by a
two-connection contention test, not a syntax check — see
[`docs/capabilities.md`](docs/capabilities.md) for why that distinction
matters, including the platform where it mattered most (Db2 accepts the
clause and silently ignores it).

Zero runtime dependencies. Plain JDBC only.

## The compatibility cost — read this before you `switch`

`Platform` is a **sealed** interface with no third-party extension point.
That is what makes the `switch` above exhaustive without a `default` arm, and
exhaustiveness at compile time is the entire value of this library. It has a
real cost: adding a permitted subtype later (say, a future `Redshift` arm)
throws `MatchException` at runtime in **already-compiled** exhaustive
switches — the compiler inserts an implicit throwing default when compiling
against an older accent, and that default fires when the jar is upgraded
without a recompile.

**Include a `default` unless you pin the version.** If you switch
exhaustively without one, treat every accent upgrade as a potential binary
break and recompile against it. This is accepted as the honest price of
compile-time certainty, not something the library works around. Most callers
don't need the exhaustive form at all — `platform.supportsSkipLocked()` is
the one capability accent currently exposes.

See [`docs/design.md`](docs/design.md) for the full argument, including why
`Platform` is sealed rather than an enum or open interface and why Redshift
isn't one of the thirteen arms.

## Coordinates

**Not yet released.** accent has not been published to Maven Central; there is
no `0.1.0` artifact to resolve yet. Once the first release ships, the
coordinates will be:

```xml
<dependency>
    <groupId>org.jwcarman.accent</groupId>
    <artifactId>accent</artifactId>
    <version>0.1.0</version>
</dependency>
```

Until then, the current version in `pom.xml` is `0.1.0-SNAPSHOT`.

## Documentation

The documentation site is live at
**[jwcarman.github.io/accent](https://jwcarman.github.io/accent/)**, built
from `main`. The page set below is part of this branch and publishes once it
merges; until then, read it directly from the repository:

- [`docs/getting-started.md`](docs/getting-started.md) — add the dependency,
  make the first call, read the result.
- [`docs/platforms.md`](docs/platforms.md) — all thirteen arms, and which
  report an impersonated version rather than the engine's own.
- [`docs/capabilities.md`](docs/capabilities.md) — `supportsSkipLocked()`: the
  contention methodology, the measured results, the version floors.
- [`docs/unknown.md`](docs/unknown.md) — why `Unknown` exists and how to
  supply a fallback for databases accent doesn't recognise.
- [`docs/design.md`](docs/design.md) — the full argument for every decision
  above.
- [`docs/contributing.md`](docs/contributing.md) — how to add a platform and
  run the full detection matrix locally.

Also in-repo: [`SPEC.md`](SPEC.md) (why accent exists, its non-goals) and
[`CHANGELOG.md`](CHANGELOG.md).

## License

Apache License, Version 2.0. See [`LICENSE`](LICENSE).
