package org.jwcarman.accent;

import java.util.Objects;

/**
 * The database accent is talking to.
 */
public sealed interface Platform {

  /** The version information the driver reported for this platform. */
  Version version();

  /** @return the raw product name, exactly as the driver reported it */
  default String productName() {
    return version().productName();
  }

  /** @return the raw product version, exactly as the driver reported it */
  default String productVersion() {
    return version().productVersion();
  }

  /** @return the database major version the driver reported */
  default int majorVersion() {
    return version().majorVersion();
  }

  /** @return the database minor version the driver reported */
  default int minorVersion() {
    return version().minorVersion();
  }

  /**
   * Whether this platform supports the {@code FOR UPDATE SKIP LOCKED} clause with genuine
   * skip-locked semantics: a second transaction reading rows locked by a first must skip them
   * rather than block.
   *
   * <p>This is deliberately narrow. It does not cover SQL Server's {@code WITH (UPDLOCK, READPAST)},
   * which is a different statement with different semantics, and it is not the first member of a
   * general capability bag.
   *
   * <p>A {@code false} answer is always safe: a caller falls back to plain {@code FOR UPDATE}, which
   * blocks instead of skipping but is never incorrect. Every arm returning {@code true} is backed by
   * a contention test in accent's integration suite.
   *
   * @return true if concurrent claims skip locked rows
   */
  default boolean supportsSkipLocked() {
    return false;
  }

  /** PostgreSQL proper, having ruled out the engines that impersonate it. */
  record PostgreSQL(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 9;
    private static final int SKIP_LOCKED_MINOR = 5;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in PostgreSQL 9.5. Verified by contention test against
     * PostgreSQL 17.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() > SKIP_LOCKED_MAJOR
          || (majorVersion() == SKIP_LOCKED_MAJOR && minorVersion() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * CockroachDB.
   *
   * <p>Through pgjdbc this engine is indistinguishable from PostgreSQL at the {@link
   * java.sql.DatabaseMetaData} level — it reports product name {@code PostgreSQL} and a bare
   * PostgreSQL version number. Its {@link #version()} therefore describes PostgreSQL, not
   * CockroachDB. accent identifies it by querying {@code SELECT version()}.
   */
  record CockroachDB(Version version) implements Platform {

    /**
     * {@inheritDoc}
     *
     * <p>Verified by contention test against CockroachDB 24.1: a second connection genuinely
     * skips a row locked by a first rather than blocking. Unconditionally {@code true} rather
     * than version-gated, because {@link #version()} here describes the PostgreSQL release
     * CockroachDB emulates, not CockroachDB's own release number — there is no meaningful major
     * or minor to gate on. No lower bound was tested; only 24.1 was measured.
     */
    @Override
    public boolean supportsSkipLocked() {
      return true;
    }
  }

  /**
   * YugabyteDB.
   *
   * <p>Reports product name {@code PostgreSQL}; its {@link #version()} describes the PostgreSQL
   * release it emulates, not YugabyteDB's own numbering.
   */
  record YugabyteDB(Version version) implements Platform {

    /**
     * {@inheritDoc}
     *
     * <p>Verified by contention test against YugabyteDB 2024.1: a second connection genuinely
     * skips a row locked by a first rather than blocking. Unconditionally {@code true} rather
     * than version-gated, because {@link #version()} here describes the PostgreSQL release
     * YugabyteDB emulates, not YugabyteDB's own release number — there is no meaningful major or
     * minor to gate on. No lower bound was tested; only 2024.1 was measured.
     */
    @Override
    public boolean supportsSkipLocked() {
      return true;
    }
  }

  /** MySQL proper, having ruled out MariaDB reached through a MySQL driver. */
  record MySQL(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 8;
    private static final int SKIP_LOCKED_MINOR = 0;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in MySQL 8.0. Verified by contention test against MySQL 8.4.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() > SKIP_LOCKED_MAJOR
          || (majorVersion() == SKIP_LOCKED_MAJOR && minorVersion() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * MariaDB.
   *
   * <p>Reached through {@code mysql-connector-j} this server reports product name {@code MySQL};
   * only its product version names MariaDB.
   */
  record MariaDB(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 10;
    private static final int SKIP_LOCKED_MINOR = 6;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in MariaDB 10.6. Verified by contention test against MariaDB
     * 11.4.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() > SKIP_LOCKED_MAJOR
          || (majorVersion() == SKIP_LOCKED_MAJOR && minorVersion() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * Microsoft SQL Server.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false} here and must stay that
   * way. SQL Server has no {@code FOR UPDATE SKIP LOCKED} clause; its nearest equivalent is
   * {@code WITH (UPDLOCK, READPAST)}, a different statement with different semantics.
   * {@code supportsSkipLocked()} is documented as covering the {@code FOR UPDATE SKIP LOCKED}
   * clause specifically, and a contention test against SQL Server 2022 using that exact clause
   * confirms it: plain {@code FOR UPDATE} itself is rejected outside a cursor declaration
   * ("FOR UPDATE clause allowed only for DECLARE CURSOR"), so no skip is ever observed. Do not
   * "fix" this arm to {@code true} on the strength of {@code READPAST} — that is a different
   * capability this predicate does not cover.
   */
  record SqlServer(Version version) implements Platform {}

  /** Oracle Database. Its product version spans two lines. */
  record Oracle(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 11;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in Oracle 11. Verified by contention test against Oracle 23.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() >= SKIP_LOCKED_MAJOR;
    }
  }

  /**
   * IBM Db2. Its product name carries a platform suffix, such as {@code DB2/LINUXX8664}.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. Db2 12.1 parses {@code FOR
   * UPDATE SKIP LOCKED} without error, but a contention test shows it does not block — it
   * ignores the clause and returns both rows, including the one the first connection still holds
   * locked in an open transaction:
   *
   * <pre>{@code did not skip: returned [1, 2] — clause accepted and ignored}</pre>
   *
   * <p>This is a worse trap than blocking would be: a caller doing outbox claiming against Db2 on
   * the strength of the syntax being accepted would hand out the same row to two workers at once,
   * silently, under concurrency — exactly the failure mode this predicate exists to prevent. Do
   * not "fix" this arm to {@code true} on the strength of the syntax being accepted; that syntax
   * being accepted is precisely what makes this arm dangerous to get wrong.
   */
  record Db2(Version version) implements Platform {}

  /** H2. */
  record H2(Version version) implements Platform {

    /**
     * {@inheritDoc}
     *
     * <p>H2 parses {@code FOR UPDATE SKIP LOCKED}, which contradicted an earlier guess that it
     * would not. Whether it genuinely skips was unknown until measured: a contention test against
     * H2 2.3.232 confirms a second connection does skip a row locked by a first rather than
     * blocking. Unconditionally {@code true} because no earlier H2 version was tested and no
     * documented floor is known; only 2.3.232 was measured.
     */
    @Override
    public boolean supportsSkipLocked() {
      return true;
    }
  }

  /**
   * HSQLDB, which reports itself as {@code HSQL Database Engine}.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. HSQLDB rejects the {@code
   * SKIP LOCKED} clause outright ({@code unexpected token: SKIP}), confirmed by contention test
   * against HSQLDB 2.7.
   */
  record HSQLDB(Version version) implements Platform {}

  /**
   * SQLite.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. SQLite has no row-level
   * locking model at all — even plain {@code FOR UPDATE} is a syntax error — confirmed by
   * contention test against SQLite 3.47.
   */
  record SQLite(Version version) implements Platform {}

  /**
   * Apache Derby.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. Derby rejects the {@code
   * SKIP LOCKED} clause outright, confirmed by contention test against Derby 10.17.
   */
  record Derby(Version version) implements Platform {}

  /**
   * A database accent has not been taught.
   *
   * <p>This arm exists so an exhaustive {@code switch} needs no {@code default}, which forces every
   * caller to decide at compile time what happens for an unrecognised database. It carries the raw
   * strings so the decision can be logged actionably and the gap reported.
   *
   * <p>{@code Unknown} means "asked, did not recognise". It never means "could not ask" — a failure
   * to reach the database raises {@link AccentException} instead.
   */
  record Unknown(Version version) implements Platform {}

  /**
   * What the JDBC driver reported, verbatim.
   *
   * <p>These values are raw. For a database that impersonates another, they describe the
   * <em>impersonation</em>, not the engine: pgjdbc reports a PostgreSQL version number for
   * CockroachDB, so a {@link CockroachDB} carries a PostgreSQL version. Never parse an engine
   * version out of these fields expecting the engine's own numbering.
   *
   * <p>{@code majorVersion} and {@code minorVersion} come from {@link
   * java.sql.DatabaseMetaData#getDatabaseMajorVersion()} and {@link
   * java.sql.DatabaseMetaData#getDatabaseMinorVersion()}. They are the only trustworthy numeric
   * source: Db2 reports a build identifier such as {@code SQL120100} as its product version, and
   * Oracle's product version spans two lines and names a marketing release that disagrees with its
   * release number.
   *
   * @param productName the driver's database product name
   * @param productVersion the driver's database product version, which may contain newlines
   * @param majorVersion the driver's database major version
   * @param minorVersion the driver's database minor version
   */
  record Version(String productName, String productVersion, int majorVersion, int minorVersion) {

    /** Validates that the reported strings are present. */
    public Version {
      Objects.requireNonNull(productName, "productName");
      Objects.requireNonNull(productVersion, "productVersion");
    }
  }
}
