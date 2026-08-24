/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.accent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jwcarman.accent.Platform.CockroachDB;
import org.jwcarman.accent.Platform.Db2;
import org.jwcarman.accent.Platform.Derby;
import org.jwcarman.accent.Platform.EngineVersion;
import org.jwcarman.accent.Platform.H2;
import org.jwcarman.accent.Platform.HSQLDB;
import org.jwcarman.accent.Platform.MariaDB;
import org.jwcarman.accent.Platform.MySQL;
import org.jwcarman.accent.Platform.Oracle;
import org.jwcarman.accent.Platform.PostgreSQL;
import org.jwcarman.accent.Platform.SQLite;
import org.jwcarman.accent.Platform.SqlServer;
import org.jwcarman.accent.Platform.Unknown;
import org.jwcarman.accent.Platform.Version;
import org.jwcarman.accent.Platform.YugabyteDB;

/**
 * Maps what a database reported onto the {@link Platform} vocabulary.
 *
 * <p>Pure by construction: no JDBC types, no I/O, no state. Every heuristic accent has lives here
 * and nowhere else, so the whole of accent's judgement is testable without a database running.
 *
 * <p>Matching is case-insensitive throughout, and uses prefix or contains tests wherever that is
 * what the driver actually guarantees. Db2's product name carries a host-architecture suffix
 * ({@code DB2/LINUXX8664}, {@code DB2/NT64}), so only a prefix test is safe. Every string this
 * class matches against was observed against a running server; see {@code
 * docs/observed-strings.md}.
 */
final class Detector {

  private static final String MARIADB_MARKER = "mariadb";
  private static final String COCKROACH_MARKER = "cockroachdb";
  private static final String YUGABYTE_MARKER = "-yb-";

  private static final Pattern COCKROACH_ENGINE_VERSION = Pattern.compile("v(\\d+)\\.(\\d+)");
  private static final Pattern YUGABYTE_ENGINE_VERSION = Pattern.compile("-YB-(\\d+)\\.(\\d+)");

  private Detector() {}

  /**
   * Whether identifying this product requires querying the server beyond its metadata.
   *
   * <p>True only for the PostgreSQL family. CockroachDB and YugabyteDB both report a product name
   * of {@code PostgreSQL}, and CockroachDB's reported version is a bare PostgreSQL version number
   * with no marker of its own, so metadata alone cannot separate the three.
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

  /**
   * Separates PostgreSQL from the engines that impersonate it.
   *
   * <p>This is the one family metadata cannot resolve, so it is resolved from {@code SELECT
   * version()}. CockroachDB's result does not begin with {@code PostgreSQL} at all; YugabyteDB's
   * embeds a {@code -YB-} marker in its version number.
   *
   * <p>A missing query yields {@link Unknown} rather than a guess. Returning {@code PostgreSQL}
   * without having ruled out CockroachDB would ship exactly the silent misidentification accent
   * exists to prevent.
   */
  private static Platform postgresFamily(Fingerprint fingerprint, Version version) {
    var rawQuery = fingerprint.versionQuery();
    var query = normalise(rawQuery);
    if (query.isEmpty()) {
      return new Unknown(version);
    }
    if (query.contains(COCKROACH_MARKER)) {
      return new CockroachDB(version, parseEngineVersion(rawQuery, COCKROACH_ENGINE_VERSION));
    }
    if (query.contains(YUGABYTE_MARKER)) {
      return new YugabyteDB(version, parseEngineVersion(rawQuery, YUGABYTE_ENGINE_VERSION));
    }
    return new PostgreSQL(version);
  }

  /**
   * Parses an impostor's own version out of {@code SELECT version()}, since {@link Version} here
   * describes the PostgreSQL release the impostor emulates rather than the engine itself.
   *
   * <p>An unparseable string yields major/minor of 0 rather than a guess — {@code
   * supportsSkipLocked()} treats that as no evidence of capability and answers {@code false}.
   *
   * @param raw the full {@code SELECT version()} result
   * @param pattern the marker-specific pattern capturing major and minor
   * @return the parsed {@link EngineVersion}
   */
  private static EngineVersion parseEngineVersion(String raw, Pattern pattern) {
    Matcher matcher = pattern.matcher(raw);
    if (matcher.find()) {
      return new EngineVersion(
          raw, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }
    return new EngineVersion(raw, 0, 0);
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
