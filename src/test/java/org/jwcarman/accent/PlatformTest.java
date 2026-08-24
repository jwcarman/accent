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

import java.util.List;
import org.junit.jupiter.api.Test;
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

class PlatformTest {

  private static final Version VERSION = new Version("PostgreSQL", "17.10", 17, 10);

  private static final EngineVersion COCKROACH_ENGINE = new EngineVersion("v24.1.32", 24, 1);
  private static final EngineVersion YUGABYTE_ENGINE =
      new EngineVersion("-YB-2024.1.0.0-b0", 2024, 1);

  private static List<Platform> allArms() {
    return List.of(
        new PostgreSQL(VERSION),
        new CockroachDB(VERSION, COCKROACH_ENGINE),
        new YugabyteDB(VERSION, YUGABYTE_ENGINE),
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
    assertThat(new CockroachDB(VERSION, COCKROACH_ENGINE).supportsSkipLocked()).isTrue();
    assertThat(new YugabyteDB(VERSION, YUGABYTE_ENGINE).supportsSkipLocked()).isTrue();
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
  void postgresWithMajorBelowTheFloorDoesNotClaimSkipLocked() {
    var old = new Version("PostgreSQL", "8.4.22", 8, 4);

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
  void mariadbWithMajorBelowTheFloorDoesNotClaimSkipLocked() {
    var old = new Version("MySQL", "5.5.68-MariaDB", 5, 5);

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
  void cockroachBelowTwentyTwoDotTwoDoesNotClaimSkipLocked() {
    // v22.1.22 genuinely does not skip: ERROR: unimplemented: SKIP LOCKED lock wait policy is
    // not supported.
    var old = new EngineVersion("CockroachDB CCL v22.1.22 (...)", 22, 1);

    assertThat(new CockroachDB(VERSION, old).supportsSkipLocked()).isFalse();
  }

  @Test
  void cockroachAtTwentyTwoDotTwoClaimsSkipLocked() {
    var boundary = new EngineVersion("CockroachDB CCL v22.2.19 (...)", 22, 2);

    assertThat(new CockroachDB(VERSION, boundary).supportsSkipLocked()).isTrue();
  }

  @Test
  void cockroachWithUnparseableEngineVersionDoesNotClaimSkipLocked() {
    // Detection still succeeded — this is CockroachDB — but an unparseable version is no
    // evidence of capability, and false is always the safe answer.
    var unparseable = new EngineVersion("some future format accent has never seen", 0, 0);

    assertThat(new CockroachDB(VERSION, unparseable).supportsSkipLocked()).isFalse();
  }

  @Test
  void yugabyteBelowTwoDotSixteenDoesNotClaimSkipLocked() {
    var old = new EngineVersion("PostgreSQL 11.2-YB-2.15.9.0-b0 on ...", 2, 15);

    assertThat(new YugabyteDB(VERSION, old).supportsSkipLocked()).isFalse();
  }

  @Test
  void yugabyteAtTwoDotSixteenClaimsSkipLocked() {
    var boundary = new EngineVersion("PostgreSQL 11.2-YB-2.16.9.0-b0 on ...", 2, 16);

    assertThat(new YugabyteDB(VERSION, boundary).supportsSkipLocked()).isTrue();
  }

  @Test
  void yugabyteWithUnparseableEngineVersionDoesNotClaimSkipLocked() {
    var unparseable = new EngineVersion("some future format accent has never seen", 0, 0);

    assertThat(new YugabyteDB(VERSION, unparseable).supportsSkipLocked()).isFalse();
  }

  @Test
  void h2BelowTwoPointTwoDoesNotClaimSkipLocked() {
    // 2.1.214 genuinely does not skip: Syntax error in SQL statement "... FOR UPDATE SKIP[*]
    // LOCKED".
    var old = new Version("H2", "2.1.214", 2, 1);

    assertThat(new H2(old).supportsSkipLocked()).isFalse();
  }

  @Test
  void h2AtTwoPointTwoClaimsSkipLocked() {
    var boundary = new Version("H2", "2.2.224", 2, 2);

    assertThat(new H2(boundary).supportsSkipLocked()).isTrue();
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
