package org.jwcarman.accent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.jwcarman.accent.Platform.Version;
import org.jwcarman.accent.Platform.YugabyteDB;

class PlatformTest {

  private static final Version VERSION = new Version("PostgreSQL", "17.10", 17, 10);

  private static List<Platform> allArms() {
    return List.of(
        new PostgreSQL(VERSION),
        new CockroachDB(VERSION),
        new YugabyteDB(VERSION),
        new MySQL(VERSION),
        new MariaDB(VERSION),
        new SqlServer(VERSION),
        new Oracle(VERSION),
        new Db2(VERSION),
        new H2(VERSION),
        new HSQLDB(VERSION),
        new SQLite(VERSION),
        new Derby(VERSION),
        new Unknown(VERSION));
  }

  @Test
  void everyArmDelegatesItsAccessorsToItsVersion() {
    assertThat(allArms())
        .allSatisfy(
            platform -> {
              assertThat(platform.productName()).isEqualTo("PostgreSQL");
              assertThat(platform.productVersion()).isEqualTo("17.10");
              assertThat(platform.majorVersion()).isEqualTo(17);
              assertThat(platform.minorVersion()).isEqualTo(10);
              assertThat(platform.version()).isEqualTo(VERSION);
            });
  }

  @Test
  void onlyArmsProvenByContentionClaimSkipLocked() {
    // VERSION is a PostgreSQL 17 reading (major 17), so PostgreSQL, MySQL, MariaDB, CockroachDB,
    // YugabyteDB, H2 and Oracle (whose floor is major 11) all answer true here. Db2 and SQL
    // Server stay false despite accepting or resembling the syntax: contention proved neither
    // genuinely skips.
    assertThat(new PostgreSQL(VERSION).supportsSkipLocked()).isTrue();
    assertThat(new MySQL(VERSION).supportsSkipLocked()).isTrue();
    assertThat(new MariaDB(VERSION).supportsSkipLocked()).isTrue();
    assertThat(new CockroachDB(VERSION).supportsSkipLocked()).isTrue();
    assertThat(new YugabyteDB(VERSION).supportsSkipLocked()).isTrue();
    assertThat(new H2(VERSION).supportsSkipLocked()).isTrue();
    assertThat(new Oracle(VERSION).supportsSkipLocked()).isTrue();

    assertThat(allArms())
        .filteredOn(
            platform ->
                !(platform instanceof PostgreSQL)
                    && !(platform instanceof MySQL)
                    && !(platform instanceof MariaDB)
                    && !(platform instanceof CockroachDB)
                    && !(platform instanceof YugabyteDB)
                    && !(platform instanceof H2)
                    && !(platform instanceof Oracle))
        .allSatisfy(platform -> assertThat(platform.supportsSkipLocked()).isFalse());
  }

  @Test
  void postgresBelowNinePointFiveDoesNotClaimSkipLocked() {
    var old = new Version("PostgreSQL", "9.4.26", 9, 4);

    assertThat(new PostgreSQL(old).supportsSkipLocked()).isFalse();
  }

  @Test
  void postgresAtNinePointFiveClaimsSkipLocked() {
    var boundary = new Version("PostgreSQL", "9.5.25", 9, 5);

    assertThat(new PostgreSQL(boundary).supportsSkipLocked()).isTrue();
  }

  @Test
  void mysqlBelowEightDoesNotClaimSkipLocked() {
    var old = new Version("MySQL", "5.7.44", 5, 7);

    assertThat(new MySQL(old).supportsSkipLocked()).isFalse();
  }

  @Test
  void mysqlAtEightClaimsSkipLocked() {
    var boundary = new Version("MySQL", "8.0.0", 8, 0);

    assertThat(new MySQL(boundary).supportsSkipLocked()).isTrue();
  }

  @Test
  void mariadbBelowTenPointSixDoesNotClaimSkipLocked() {
    var old = new Version("MySQL", "10.5.24-MariaDB", 10, 5);

    assertThat(new MariaDB(old).supportsSkipLocked()).isFalse();
  }

  @Test
  void mariadbAtTenPointSixClaimsSkipLocked() {
    var boundary = new Version("MySQL", "10.6.0-MariaDB", 10, 6);

    assertThat(new MariaDB(boundary).supportsSkipLocked()).isTrue();
  }

  @Test
  void oracleBelowElevenDoesNotClaimSkipLocked() {
    var old = new Version("Oracle", "10.2.0.5.0", 10, 2);

    assertThat(new Oracle(old).supportsSkipLocked()).isFalse();
  }

  @Test
  void oracleAtElevenClaimsSkipLocked() {
    var boundary = new Version("Oracle", "11.0.0.0.0", 11, 0);

    assertThat(new Oracle(boundary).supportsSkipLocked()).isTrue();
  }

  @Test
  void theVocabularyIsExhaustivelySwitchableWithoutADefault() {
    // This method does not compile if an arm is added and not handled. That is the product.
    Platform platform = new Unknown(VERSION);
    var name =
        switch (platform) {
          case PostgreSQL _ -> "postgresql";
          case CockroachDB _ -> "cockroachdb";
          case YugabyteDB _ -> "yugabytedb";
          case MySQL _ -> "mysql";
          case MariaDB _ -> "mariadb";
          case SqlServer _ -> "sqlserver";
          case Oracle _ -> "oracle";
          case Db2 _ -> "db2";
          case H2 _ -> "h2";
          case HSQLDB _ -> "hsqldb";
          case SQLite _ -> "sqlite";
          case Derby _ -> "derby";
          case Unknown _ -> "unknown";
        };

    assertThat(name).isEqualTo("unknown");
  }
}
