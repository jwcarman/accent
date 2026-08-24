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
    void detectsYugabyte() {
      var fingerprint =
          queried(
              ObservedStrings.YUGABYTE_NAME,
              ObservedStrings.YUGABYTE_VERSION,
              ObservedStrings.YUGABYTE_VERSION_QUERY);

      assertThat(Detector.detect(fingerprint)).isInstanceOf(YugabyteDB.class);
    }

    @Test
    void doesNotMistakeCockroachForPostgresBecauseTheVersionQueryLeads() {
      // Cockroach's version() does not even begin with "PostgreSQL".
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
