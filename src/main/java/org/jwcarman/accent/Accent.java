package org.jwcarman.accent;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import org.jwcarman.accent.Platform.Version;

/**
 * Answers one question about a JDBC connection: which database is this?
 *
 * <pre>{@code
 * Platform platform = Accent.of(dataSource);
 *
 * String claimSql = switch (platform) {
 *     case PostgreSQL p  -> "SELECT ... FOR UPDATE SKIP LOCKED";
 *     case CockroachDB c -> "SELECT ... FOR UPDATE";
 *     case Unknown u     -> throw new UnsupportedOperationException(u.productName());
 *     // every other arm
 * };
 * }</pre>
 *
 * <p>Detection reads {@link DatabaseMetaData}. For the PostgreSQL family only, it also issues
 * {@code SELECT version()}, because CockroachDB and YugabyteDB both report a product name of {@code
 * PostgreSQL} and metadata cannot separate them. No other family incurs that round trip.
 */
public final class Accent {

  private static final String VERSION_QUERY = "SELECT version()";

  private Accent() {}

  /**
   * Identifies the database behind a {@link DataSource}.
   *
   * <p>Opens a connection and closes it before returning.
   *
   * @param dataSource the data source to interrogate
   * @return the platform behind it, or {@link Platform.Unknown} if accent does not recognise it
   * @throws AccentException if the database could not be reached or read
   */
  public static Platform of(DataSource dataSource) {
    return builder().of(dataSource);
  }

  /**
   * Identifies the database behind a {@link Connection}.
   *
   * <p>Does not close the connection.
   *
   * @param connection the connection to interrogate
   * @return the platform behind it, or {@link Platform.Unknown} if accent does not recognise it
   * @throws AccentException if the database could not be read
   */
  public static Platform of(Connection connection) {
    return builder().of(connection);
  }

  /**
   * Identifies the database behind a {@link DatabaseMetaData}.
   *
   * <p>For the PostgreSQL family this reaches the connection via {@link
   * DatabaseMetaData#getConnection()} to issue {@code SELECT version()}.
   *
   * @param metaData the metadata to interrogate
   * @return the platform behind it, or {@link Platform.Unknown} if accent does not recognise it
   * @throws AccentException if the database could not be read
   */
  public static Platform of(DatabaseMetaData metaData) {
    return builder().of(metaData);
  }

  /**
   * @return a builder for callers who need to supply a fallback
   */
  public static Builder builder() {
    return new Builder(null);
  }

  /**
   * Configures detection for callers who can identify a database accent cannot.
   *
   * <p>Anything sitting behind a connection pooler or a managed service may report strings accent
   * has never seen — pgbouncer, Aurora, or H2 in PostgreSQL-compatibility mode. A fallback turns
   * "accent did not recognise this" from a dead end into a question the caller can answer.
   */
  public static final class Builder {

    private final Function<Version, Platform> fallback;

    private Builder(Function<Version, Platform> fallback) {
      this.fallback = fallback;
    }

    /**
     * Supplies the platform to use when detection would otherwise return {@link Platform.Unknown}.
     *
     * <p>The function receives the raw readings and may return {@code null} to accept {@code
     * Unknown}.
     *
     * @param fallback the caller's own identification
     * @return a builder using it
     */
    public Builder fallback(Function<Version, Platform> fallback) {
      return new Builder(Objects.requireNonNull(fallback, "fallback"));
    }

    /**
     * Identifies the database behind a {@link DataSource}, opening and closing a connection.
     *
     * @param dataSource the data source to interrogate
     * @return the platform behind it
     * @throws AccentException if the database could not be reached or read
     */
    public Platform of(DataSource dataSource) {
      try (Connection connection = dataSource.getConnection()) {
        return of(connection);
      } catch (SQLException e) {
        throw new AccentException("could not open a connection to identify the database", e);
      }
    }

    /**
     * Identifies the database behind a {@link Connection}, which is left open.
     *
     * @param connection the connection to interrogate
     * @return the platform behind it
     * @throws AccentException if the database could not be read
     */
    public Platform of(Connection connection) {
      try {
        return of(connection.getMetaData());
      } catch (SQLException e) {
        throw new AccentException("could not read database metadata", e);
      }
    }

    /**
     * Identifies the database behind a {@link DatabaseMetaData}.
     *
     * @param metaData the metadata to interrogate
     * @return the platform behind it
     * @throws AccentException if the database could not be read
     */
    public Platform of(DatabaseMetaData metaData) {
      var platform = Detector.detect(fingerprint(metaData));
      if (fallback == null || !(platform instanceof Platform.Unknown)) {
        return platform;
      }
      var supplied = fallback.apply(platform.version());
      return supplied == null ? platform : supplied;
    }

    private static Fingerprint fingerprint(DatabaseMetaData metaData) {
      try {
        var productName = metaData.getDatabaseProductName();
        var versionQuery =
            Detector.needsVersionQuery(productName) ? queryVersion(metaData.getConnection()) : null;
        return new Fingerprint(
            productName,
            metaData.getDatabaseProductVersion(),
            metaData.getDatabaseMajorVersion(),
            metaData.getDatabaseMinorVersion(),
            versionQuery);
      } catch (SQLException e) {
        throw new AccentException("could not read database metadata", e);
      }
    }

    private static String queryVersion(Connection connection) throws SQLException {
      try (var statement = connection.createStatement();
          var resultSet = statement.executeQuery(VERSION_QUERY)) {
        return resultSet.next() ? resultSet.getString(1) : null;
      }
    }
  }
}
