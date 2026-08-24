package org.jwcarman.accent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.junit.jupiter.api.Test;

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
  void noArmClaimsSkipLockedUntilContentionProvesIt() {
    // Task 13 flips these to true one at a time, each backed by a contention test.
    assertThat(allArms()).allSatisfy(platform -> assertThat(platform.supportsSkipLocked()).isFalse());
  }

  @Test
  void theVocabularyIsExhaustivelySwitchableWithoutADefault() {
    // This method does not compile if an arm is added and not handled. That is the product.
    Platform platform = new Unknown(VERSION);
    var name =
        switch (platform) {
          case PostgreSQL p -> "postgresql";
          case CockroachDB p -> "cockroachdb";
          case YugabyteDB p -> "yugabytedb";
          case MySQL p -> "mysql";
          case MariaDB p -> "mariadb";
          case SqlServer p -> "sqlserver";
          case Oracle p -> "oracle";
          case Db2 p -> "db2";
          case H2 p -> "h2";
          case HSQLDB p -> "hsqldb";
          case SQLite p -> "sqlite";
          case Derby p -> "derby";
          case Unknown p -> "unknown";
        };

    assertThat(name).isEqualTo("unknown");
  }
}
