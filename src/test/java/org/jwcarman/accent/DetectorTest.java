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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Platform.CockroachDB;
import org.jwcarman.accent.Platform.Db2;
import org.jwcarman.accent.Platform.Derby;
import org.jwcarman.accent.Platform.H2;
import org.jwcarman.accent.Platform.HSQLDB;
import org.jwcarman.accent.Platform.MariaDB;
import org.jwcarman.accent.Platform.MySQL;
import org.jwcarman.accent.Platform.Oracle;
import org.jwcarman.accent.Platform.PostgreSQL;
import org.jwcarman.accent.Platform.SQLite;
import org.jwcarman.accent.Platform.SqlServer;
import org.jwcarman.accent.Platform.Unknown;
import org.jwcarman.accent.Platform.YugabyteDB;

class DetectorTest {

  private static Fingerprint metadataOnly(String productName, String productVersion) {
    return new Fingerprint(productName, productVersion, 0, 0, null);
  }

  @Nested
  class MySqlFamily {

    @Test
    void detectsMySql() {
      var fingerprint = metadataOnly(ObservedStrings.MYSQL_NAME, ObservedStrings.MYSQL_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(MySQL.class);
    }

    @Test
    void detectsMariaDbThroughItsOwnDriver() {
      var fingerprint = metadataOnly(ObservedStrings.MARIADB_NAME, ObservedStrings.MARIADB_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(MariaDB.class);
    }

    @Test
    void detectsMariaDbImpersonatingMySql() {
      // The load-bearing case: same server, MySQL driver, product name says MySQL.
      var fingerprint =
          metadataOnly(ObservedStrings.MARIADB_VIA_MYSQL_NAME, ObservedStrings.MARIADB_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(MariaDB.class);
    }

    @Test
    void matchesTheMariaDbMarkerRegardlessOfCase() {
      var fingerprint = metadataOnly("mysql", "11.4.12-mariadb-ubu2404");

      assertThat(Detector.detect(fingerprint)).isInstanceOf(MariaDB.class);
    }
  }

  @Nested
  class OtherEngines {

    @Test
    void detectsSqlServer() {
      var fingerprint =
          metadataOnly(ObservedStrings.SQLSERVER_NAME, ObservedStrings.SQLSERVER_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(SqlServer.class);
    }

    @Test
    void detectsOracleDespiteItsTwoLineVersion() {
      var fingerprint = metadataOnly(ObservedStrings.ORACLE_NAME, ObservedStrings.ORACLE_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(Oracle.class);
    }

    @Test
    void detectsDb2ByPrefixBecauseTheSuffixIsArchitectureSpecific() {
      var fingerprint = metadataOnly(ObservedStrings.DB2_NAME, ObservedStrings.DB2_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(Db2.class);
    }

    @Test
    void detectsDb2OnOtherArchitectures() {
      assertThat(Detector.detect(metadataOnly("DB2/NT64", "SQL120100"))).isInstanceOf(Db2.class);
      assertThat(Detector.detect(metadataOnly("DB2/AIX64", "SQL120100"))).isInstanceOf(Db2.class);
    }

    @Test
    void detectsH2() {
      assertThat(Detector.detect(metadataOnly(ObservedStrings.H2_NAME, ObservedStrings.H2_VERSION)))
          .isInstanceOf(H2.class);
    }

    @Test
    void detectsHsqldbByItsProseProductName() {
      var fingerprint = metadataOnly(ObservedStrings.HSQLDB_NAME, ObservedStrings.HSQLDB_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(HSQLDB.class);
    }

    @Test
    void detectsSqlite() {
      var fingerprint = metadataOnly(ObservedStrings.SQLITE_NAME, ObservedStrings.SQLITE_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(SQLite.class);
    }

    @Test
    void detectsDerby() {
      var fingerprint = metadataOnly(ObservedStrings.DERBY_NAME, ObservedStrings.DERBY_VERSION);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(Derby.class);
    }
  }

  @Nested
  class UnrecognisedDatabases {

    @Test
    void fallToUnknownCarryingTheRawStrings() {
      var fingerprint = new Fingerprint("Informix Dynamic Server", "14.10.FC9W1", 14, 10, null);

      var platform = Detector.detect(fingerprint);

      assertThat(platform).isInstanceOf(Unknown.class);
      assertThat(platform.productName()).isEqualTo("Informix Dynamic Server");
      assertThat(platform.productVersion()).isEqualTo("14.10.FC9W1");
      assertThat(platform.majorVersion()).isEqualTo(14);
      assertThat(platform.minorVersion()).isEqualTo(10);
    }
  }

  @Nested
  class PostgresFamily {

    private static Fingerprint queried(String productName, String productVersion, String query) {
      return new Fingerprint(productName, productVersion, 0, 0, query);
    }

    @Test
    void detectsPostgresProper() {
      var fingerprint =
          queried(
              ObservedStrings.POSTGRES_NAME,
              ObservedStrings.POSTGRES_VERSION,
              ObservedStrings.POSTGRES_VERSION_QUERY);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(PostgreSQL.class);
    }

    @Test
    void detectsCockroachWhoseMetadataIsIndistinguishableFromPostgres() {
      // productName is "PostgreSQL" and productVersion is a bare "13.0.0".
      // Only the version() query gives it away.
      var fingerprint =
          queried(
              ObservedStrings.COCKROACH_NAME,
              ObservedStrings.COCKROACH_VERSION,
              ObservedStrings.COCKROACH_VERSION_QUERY);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(CockroachDB.class);
    }

    @Test
    void parsesCockroachsOwnVersionOutOfTheVersionQuery() {
      var fingerprint =
          queried(
              ObservedStrings.COCKROACH_NAME,
              ObservedStrings.COCKROACH_VERSION,
              ObservedStrings.COCKROACH_VERSION_QUERY);

      var platform = (CockroachDB) Detector.detect(fingerprint);

      assertThat(platform.engine().raw()).isEqualTo(ObservedStrings.COCKROACH_VERSION_QUERY);
      assertThat(platform.engine().major()).isEqualTo(24);
      assertThat(platform.engine().minor()).isEqualTo(1);
    }

    @Test
    void parsesCockroachsBelowFloorVersionAndReportsNoSkipLocked() {
      var fingerprint =
          queried("PostgreSQL", "13.0.0", ObservedStrings.COCKROACH_VERSION_QUERY_V22_1);

      var platform = (CockroachDB) Detector.detect(fingerprint);

      assertThat(platform.engine().major()).isEqualTo(22);
      assertThat(platform.engine().minor()).isEqualTo(1);
      assertThat(platform.supportsSkipLocked()).isFalse();
    }

    @Test
    void anUnparseableCockroachVersionQueryYieldsZeroMajorAndMinor() {
      var fingerprint = queried("PostgreSQL", "13.0.0", "cockroachdb, but no version number here");

      var platform = (CockroachDB) Detector.detect(fingerprint);

      assertThat(platform.engine().major()).isEqualTo(0);
      assertThat(platform.engine().minor()).isEqualTo(0);
    }

    @Test
    void detectsYugabyte() {
      var fingerprint =
          queried(
              ObservedStrings.YUGABYTE_NAME,
              ObservedStrings.YUGABYTE_VERSION,
              ObservedStrings.YUGABYTE_VERSION_QUERY);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(YugabyteDB.class);
    }

    @Test
    void parsesYugabytesOwnVersionOutOfTheVersionQuery() {
      var fingerprint =
          queried(
              ObservedStrings.YUGABYTE_NAME,
              ObservedStrings.YUGABYTE_VERSION,
              ObservedStrings.YUGABYTE_VERSION_QUERY);

      var platform = (YugabyteDB) Detector.detect(fingerprint);

      assertThat(platform.engine().raw()).isEqualTo(ObservedStrings.YUGABYTE_VERSION_QUERY);
      assertThat(platform.engine().major()).isEqualTo(2024);
      assertThat(platform.engine().minor()).isEqualTo(1);
    }

    @Test
    void anUnparseableYugabyteVersionQueryYieldsZeroMajorAndMinor() {
      var fingerprint =
          queried("PostgreSQL", "11.2", "postgresql 11.2-YB- but no version number follows");

      var platform = (YugabyteDB) Detector.detect(fingerprint);

      assertThat(platform.engine().major()).isEqualTo(0);
      assertThat(platform.engine().minor()).isEqualTo(0);
    }

    @Test
    void cockroachVersionQueryPinsTheObservedStringThatDoesNotStartWithPostgres() {
      // Pins the observed fixture, not production code: detectsCockroachWhoseMetadataIsIndis-
      // tinguishableFromPostgres already exercises Detector against this same fixture. This test
      // exists so a future edit to ObservedStrings.COCKROACH_VERSION_QUERY that accidentally makes
      // it start with "PostgreSQL" is caught here, at the fixture, rather than only downstream.
      assertThat(ObservedStrings.COCKROACH_VERSION_QUERY).doesNotStartWith("PostgreSQL");
    }

    @Test
    void matchesTheMarkersRegardlessOfCase() {
      assertThat(Detector.detect(queried("postgresql", "13.0.0", "cockroachdb ccl v24.1.32")))
          .isInstanceOf(CockroachDB.class);
      assertThat(Detector.detect(queried("postgresql", "11.2", "postgresql 11.2-yb-2024.1.0.0-b0")))
          .isInstanceOf(YugabyteDB.class);
    }

    @Test
    void refusesToGuessWhenTheQueryIsMissing() {
      // Without version() there is no way to rule out an impostor, and guessing "PostgreSQL"
      // is the precise bug accent exists to prevent. Unknown is the honest answer.
      var fingerprint =
          queried(ObservedStrings.POSTGRES_NAME, ObservedStrings.POSTGRES_VERSION, null);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(Unknown.class);
    }
  }

  @Nested
  class VersionQueryDispatch {

    @Test
    void onlyThePostgresFamilyNeedsAQuery() {
      assertThat(Detector.needsVersionQuery("PostgreSQL")).isTrue();
      assertThat(Detector.needsVersionQuery("postgresql")).isTrue();
      assertThat(Detector.needsVersionQuery(ObservedStrings.MYSQL_NAME)).isFalse();
      assertThat(Detector.needsVersionQuery(ObservedStrings.ORACLE_NAME)).isFalse();
      assertThat(Detector.needsVersionQuery(ObservedStrings.DB2_NAME)).isFalse();
    }
  }

  @Test
  void carriesTheReportedVersionOntoEveryArm() {
    var fingerprint =
        new Fingerprint(ObservedStrings.MYSQL_NAME, ObservedStrings.MYSQL_VERSION, 8, 4, null);

    var platform = Detector.detect(fingerprint);

    assertThat(platform.productName()).isEqualTo(ObservedStrings.MYSQL_NAME);
    assertThat(platform.productVersion()).isEqualTo(ObservedStrings.MYSQL_VERSION);
    assertThat(platform.majorVersion()).isEqualTo(8);
    assertThat(platform.minorVersion()).isEqualTo(4);
  }
}
