# Unknown

```java
record Unknown(Version version) implements Platform {}
```

## Why it exists

`Unknown` is the load-bearing idea in accent's design, not an afterthought.
`Platform` is sealed with thirteen concrete arms plus `Unknown` — no
third-party extension point, so a `switch` over `Platform` is exhaustive
*without* a `default` clause. That's only possible because there's a
permanent arm to fall into for a database accent hasn't been taught.

This is a **compile-time forcing function**. Without `Unknown`, callers who
skip a `default` clause get a `MatchException` at runtime for a database
accent doesn't recognise — which is exactly the failure mode a sealed type is
supposed to prevent. With `Unknown`, the compiler forces every caller to
write the unrecognised case deliberately, at the call site, before the code
ships:

```java
String claimSql = switch (platform) {
    case PostgreSQL p when p.supportsSkipLocked() -> "SELECT ... FOR UPDATE SKIP LOCKED";
    // ...
    case Unknown u -> throw new UnsupportedOperationException(
        "accent doesn't recognise " + u.productName());
};
```

`Unknown` carries the same `Version` every other arm does, so that
`throw`/log/report line has something actionable to say — the raw
`productName` and `productVersion` the driver reported, not just the word
"unknown."

## What it never means

**`Unknown` means "asked, did not recognise." It never means "could not
ask."** Those are different facts, and accent does not collapse them. If the
database can't be reached or `DatabaseMetaData` can't be read, `Accent.of(...)`
throws an unchecked `AccentException` carrying the underlying `SQLException` —
it does not paper over the failure by returning `Unknown`. A caller catching
`AccentException` is handling connectivity; a caller matching `Unknown` is
handling identity.

## Supplying a fallback

Some deployments sit behind something that reports a product name or version
accent has never seen: a connection pooler like pgbouncer, a managed service
like Aurora, or an embedded engine running in a compatibility mode (H2 in
PostgreSQL-compatibility mode, for instance, may report strings accent's
heuristics don't match). For those, `Accent.builder()` accepts a fallback
consulted only when detection would otherwise return `Unknown`:

```java
Platform platform = Accent.builder()
    .fallback(version -> version.productName().toLowerCase().contains("aurora")
        ? new Platform.PostgreSQL(version)
        : null)
    .of(dataSource);
```

The fallback function receives the raw `Version` accent read — the same
`productName`, `productVersion`, `majorVersion`, `minorVersion` `Unknown`
would have carried — and may return:

- a `Platform` instance, which `Accent` returns instead of `Unknown`, or
- `null`, which accepts `Unknown` as the final answer.

The fallback is never consulted when detection succeeds; it only runs for the
`Unknown` case, so it can't override a platform accent already identified.

This turns "detection failed, dead end" into "detection failed, here's how to
tell accent the answer" — API rather than an exception message the caller has
to work around. If accent recognizes the gap as common and verifiable, it may
become a thirteenth-plus-one arm later (see
[Contributing](contributing.md)); until then, the fallback is how a caller
closes it locally without waiting on a release.
