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
  record PostgreSQL(Version version) implements Platform {}

  /**
   * CockroachDB.
   *
   * <p>Through pgjdbc this engine is indistinguishable from PostgreSQL at the {@link
   * java.sql.DatabaseMetaData} level — it reports product name {@code PostgreSQL} and a bare
   * PostgreSQL version number. Its {@link #version()} therefore describes PostgreSQL, not
   * CockroachDB. accent identifies it by querying {@code SELECT version()}.
   */
  record CockroachDB(Version version) implements Platform {}

  /**
   * YugabyteDB.
   *
   * <p>Reports product name {@code PostgreSQL}; its {@link #version()} describes the PostgreSQL
   * release it emulates, not YugabyteDB's own numbering.
   */
  record YugabyteDB(Version version) implements Platform {}

  /** MySQL proper, having ruled out MariaDB reached through a MySQL driver. */
  record MySQL(Version version) implements Platform {}

  /**
   * MariaDB.
   *
   * <p>Reached through {@code mysql-connector-j} this server reports product name {@code MySQL};
   * only its product version names MariaDB.
   */
  record MariaDB(Version version) implements Platform {}

  /** Microsoft SQL Server. */
  record SqlServer(Version version) implements Platform {}

  /** Oracle Database. Its product version spans two lines. */
  record Oracle(Version version) implements Platform {}

  /** IBM Db2. Its product name carries a platform suffix, such as {@code DB2/LINUXX8664}. */
  record Db2(Version version) implements Platform {}

  /** H2. */
  record H2(Version version) implements Platform {}

  /** HSQLDB, which reports itself as {@code HSQL Database Engine}. */
  record HSQLDB(Version version) implements Platform {}

  /** SQLite. */
  record SQLite(Version version) implements Platform {}

  /** Apache Derby. */
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
