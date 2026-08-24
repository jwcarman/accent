# accent

Which database is this JDBC connection actually talking to?

> Every one of these databases speaks SQL. They differ by *accent*.

`DatabaseMetaData.getDatabaseProductName()` answers a related question badly.
CockroachDB and YugabyteDB both report `PostgreSQL` when reached through
pgjdbc — they speak the wire protocol and satisfy the metadata call
identically, while differing on exactly the things callers branch on. And
MariaDB reached through `mysql-connector-j` reports `MySQL`; reached through
`mariadb-java-client` it reports `MariaDB` — the same server, two identities,
depending on a dependency choice made somewhere else.

accent curates these impostors into a sealed `Platform` vocabulary, so a
caller can `switch` over the result and let the compiler force every case to
be handled:

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
than being assumed — the switch below can never emit `SKIP LOCKED` against a
version below the floor that predicate encodes:

```java
String claimSql = switch (platform) {
    // Genuinely skips regardless of version — see capabilities.md.
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
[Capabilities](capabilities.md) for why that distinction matters, including
the platform where it mattered most (Db2 accepts the clause and silently
ignores it).

Zero runtime dependencies. Plain JDBC only.

## Installation

accent is published to Maven Central:

```xml
<dependency>
    <groupId>org.jwcarman.accent</groupId>
    <artifactId>accent</artifactId>
    <version>0.1.0</version>
</dependency>
```

accent has zero runtime dependencies — plain JDBC only.

## The compatibility cost

`Platform` is a **sealed** interface with no third-party extension point —
that's what makes the switch above exhaustive without a `default` arm. It has
a real cost: adding a platform later throws `MatchException` at runtime in
already-compiled exhaustive switches. Read [Design](design.md) before you rely
on exhaustiveness across a version upgrade.

## Where to next

- [Getting Started](getting-started.md) — add the dependency, make the first
  call, read the result.
- [Platforms](platforms.md) — all thirteen arms, what each is detected on, and
  which ones report an impersonated version rather than the engine's own.
- [Capabilities](capabilities.md) — `supportsSkipLocked()`: the methodology,
  the measured results, the version floors.
- [Unknown](unknown.md) — why `Unknown` exists and how to supply a fallback
  for databases accent doesn't recognise.
- [Design](design.md) — why sealed, why `SELECT version()`, why contention
  tests, and the compatibility cost in full.
- [Contributing](contributing.md) — how to add a platform.
- [Observed Strings](observed-strings.md) — the raw measurements everything
  above rests on.
