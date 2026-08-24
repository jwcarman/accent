# accent

Which database is this JDBC connection actually talking to?

> Every one of these databases speaks SQL. They differ by *accent*.

`DatabaseMetaData.getDatabaseProductName()` answers a related question badly.
CockroachDB, YugabyteDB, and Amazon Redshift all report `PostgreSQL` when
reached through pgjdbc; MariaDB reported through `mysql-connector-j` reports
`MySQL`. accent curates these impostors into a sealed `Platform` vocabulary,
so callers can `switch` over the result and let the compiler force every case
to be handled:

```java
Platform platform = Accent.of(dataSource);

// Most callers stop here — one measured predicate, not a switch.
String claimSql = platform.supportsSkipLocked()
    ? "SELECT ... FOR UPDATE SKIP LOCKED"
    : "SELECT ... FOR UPDATE";
```

Callers who need the exact per-platform SQL can switch exhaustively instead;
grouping is a feature of the sealed design, so every arm that genuinely skips
a locked row shares one `case` label:

```java
String claimSql = switch (platform) {
    case PostgreSQL _, CockroachDB _, YugabyteDB _, MySQL _, MariaDB _, Oracle _, H2 _
        -> "SELECT ... FOR UPDATE SKIP LOCKED";
    case SqlServer s -> "SELECT ... WITH (UPDLOCK, READPAST)"; // a different statement, not a spelling
    case Db2 _, HSQLDB _, SQLite _, Derby _, Unknown _ -> "SELECT ... FOR UPDATE"; // safe fallback
};
```

Zero runtime dependencies. Plain JDBC only.

For the full argument — why detection issues `SELECT version()`, the
compatibility cost of a sealed hierarchy, what accent is and is not for, why
Redshift is absent, and how to run the detection matrix locally — see the
[project README](https://github.com/jwcarman/accent#readme).

## Reference

[Observed Strings](observed-strings.md) records what real drivers actually
report, measured against live containers, including the contention test that
distinguishes genuine `SKIP LOCKED` support from a database that merely parses
the clause. It is the source of truth the detection heuristics are written
against — not a hypothesis, a measurement.
