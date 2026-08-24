package org.jwcarman.accent;

import java.util.Objects;

/**
 * The database accent is talking to.
 *
 * <p>Not yet {@code sealed}: a sealed type requires at least one permitted subtype, and the
 * thirteen platform arms arrive with the vocabulary. Sealing happens then, and the
 * exhaustiveness guarantee it provides is the point of this type.
 */
public interface Platform {

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
