package org.jwcarman.accent;

import java.util.Locale;
import org.jwcarman.accent.Platform.Db2;
import org.jwcarman.accent.Platform.Derby;
import org.jwcarman.accent.Platform.H2;
import org.jwcarman.accent.Platform.HSQLDB;
import org.jwcarman.accent.Platform.MariaDB;
import org.jwcarman.accent.Platform.MySQL;
import org.jwcarman.accent.Platform.Oracle;
import org.jwcarman.accent.Platform.SQLite;
import org.jwcarman.accent.Platform.SqlServer;
import org.jwcarman.accent.Platform.Unknown;
import org.jwcarman.accent.Platform.Version;

/**
 * Maps what a database reported onto the {@link Platform} vocabulary.
 *
 * <p>Pure by construction: no JDBC types, no I/O, no state. Every heuristic accent has lives here
 * and nowhere else, so the whole of accent's judgement is testable without a database running.
 *
 * <p>Matching is case-insensitive throughout, and uses prefix or contains tests wherever that is
 * what the driver actually guarantees. Db2's product name carries a host-architecture suffix
 * ({@code DB2/LINUXX8664}, {@code DB2/NT64}), so only a prefix test is safe. Every string this class
 * matches against was observed against a running server; see {@code docs/observed-strings.md}.
 */
final class Detector {

  private static final String MARIADB_MARKER = "mariadb";

  private Detector() {}

  /**
   * Whether identifying this product requires querying the server beyond its metadata.
   *
   * <p>True only for the PostgreSQL family. CockroachDB and YugabyteDB both report a product name of
   * {@code PostgreSQL}, and CockroachDB's reported version is a bare PostgreSQL version number with
   * no marker of its own, so metadata alone cannot separate the three.
   *
   * @param productName the reported database product name
   * @return true if {@link Fingerprint#versionQuery()} must be populated for accurate detection
   */
  static boolean needsVersionQuery(String productName) {
    return "postgresql".equals(normalise(productName));
  }

  /**
   * Identifies the platform behind a set of readings.
   *
   * @param fingerprint what the database reported
   * @return the matching platform, or {@link Unknown} if accent has not been taught this database
   */
  static Platform detect(Fingerprint fingerprint) {
    var version = fingerprint.toVersion();
    var name = normalise(fingerprint.productName());

    if ("postgresql".equals(name)) {
      return postgresFamily(fingerprint, version);
    }
    if ("mysql".equals(name)) {
      return isMariaDb(fingerprint) ? new MariaDB(version) : new MySQL(version);
    }
    if (MARIADB_MARKER.equals(name)) {
      return new MariaDB(version);
    }
    if (name.startsWith("microsoft sql server")) {
      return new SqlServer(version);
    }
    if (name.startsWith("oracle")) {
      return new Oracle(version);
    }
    if (name.startsWith("db2")) {
      return new Db2(version);
    }
    if ("h2".equals(name)) {
      return new H2(version);
    }
    if (name.startsWith("hsql")) {
      return new HSQLDB(version);
    }
    if ("sqlite".equals(name)) {
      return new SQLite(version);
    }
    if (name.contains("derby")) {
      return new Derby(version);
    }
    return new Unknown(version);
  }

  /** Filled in by Task 6. */
  private static Platform postgresFamily(Fingerprint fingerprint, Version version) {
    return new Unknown(version);
  }

  /**
   * MariaDB reached through {@code mysql-connector-j} reports a product name of {@code MySQL}. The
   * only thing naming it is its product version.
   */
  private static boolean isMariaDb(Fingerprint fingerprint) {
    return normalise(fingerprint.productVersion()).contains(MARIADB_MARKER);
  }

  private static String normalise(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
