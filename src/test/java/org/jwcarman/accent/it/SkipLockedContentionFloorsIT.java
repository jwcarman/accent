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
package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Accent;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.YugabyteDBYSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Below-floor contention tests — the tests that catch overclaiming, which is the bug class {@code
 * supportsSkipLocked()} exists to prevent.
 *
 * <p>Every other contention suite in this project proves an arm's {@code true} is honest. This one
 * proves the opposite direction: that a version genuinely below a measured floor is reported as
 * {@code false}, not just assumed to be. Each test stands up a below-floor container, runs the same
 * {@link SkipLockedContention} harness as {@link SkipLockedContentionIT}, and asserts both that
 * contention does not skip AND that accent agrees.
 *
 * <p>Tagged {@code floors} and excluded from a default {@code mvn verify} — these containers are
 * old and only pulled deliberately. Run with {@code -Dexcluded.test.groups=}, as CI does.
 */
@Tag("floors")
@Testcontainers
class SkipLockedContentionFloorsIT {

  @Container
  private static final CockroachContainer COCKROACH_V22_1 =
      new CockroachContainer(DockerImageName.parse("cockroachdb/cockroach:v22.1.22"));

  @Test
  void cockroachV22Point1DoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new org.postgresql.Driver(),
                COCKROACH_V22_1.getJdbcUrl(),
                COCKROACH_V22_1.getUsername(),
                COCKROACH_V22_1.getPassword());
        var second =
            Drivers.connect(
                new org.postgresql.Driver(),
                COCKROACH_V22_1.getJdbcUrl(),
                COCKROACH_V22_1.getUsername(),
                COCKROACH_V22_1.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // v22.1.22 genuinely does not skip: ERROR: unimplemented: SKIP LOCKED lock wait policy is
      // not supported. The floor is 22.2 — see Platform.CockroachDB.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final PostgreSQLContainer<?> POSTGRES_9_4 =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:9.4"));

  @Test
  void postgres9Point4DoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new org.postgresql.Driver(),
                POSTGRES_9_4.getJdbcUrl(),
                POSTGRES_9_4.getUsername(),
                POSTGRES_9_4.getPassword());
        var second =
            Drivers.connect(
                new org.postgresql.Driver(),
                POSTGRES_9_4.getJdbcUrl(),
                POSTGRES_9_4.getUsername(),
                POSTGRES_9_4.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // 9.4.26 genuinely does not skip: syntax error at or near "SKIP". The floor is 9.5 — see
      // Platform.PostgreSQL.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final MySQLContainer<?> MYSQL_5_7 =
      new MySQLContainer<>(DockerImageName.parse("mysql:5.7"));

  @Test
  void mysql5Point7DoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new com.mysql.cj.jdbc.Driver(),
                MYSQL_5_7.getJdbcUrl(),
                MYSQL_5_7.getUsername(),
                MYSQL_5_7.getPassword());
        var second =
            Drivers.connect(
                new com.mysql.cj.jdbc.Driver(),
                MYSQL_5_7.getJdbcUrl(),
                MYSQL_5_7.getUsername(),
                MYSQL_5_7.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // 5.7.44 genuinely does not skip: a SQL syntax error near 'SKIP LOCKED'. The floor is 8.0
      // — see Platform.MySQL.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final MariaDBContainer<?> MARIADB_10_5 =
      new MariaDBContainer<>(DockerImageName.parse("mariadb:10.5"));

  @Test
  void mariadb10Point5DoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new org.mariadb.jdbc.Driver(),
                MARIADB_10_5.getJdbcUrl(),
                MARIADB_10_5.getUsername(),
                MARIADB_10_5.getPassword());
        var second =
            Drivers.connect(
                new org.mariadb.jdbc.Driver(),
                MARIADB_10_5.getJdbcUrl(),
                MARIADB_10_5.getUsername(),
                MARIADB_10_5.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // 10.5.29 genuinely does not skip: a SQL syntax error near 'SKIP'. The floor is 10.6 —
      // see Platform.MariaDB.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  // At-floor tests below: no below-floor image exists for Oracle or YugabyteDB (no Oracle 10g
  // image is published; no YugabyteDB below 2.16.9 would start on the test machine), so the
  // meaningful regression here is that the lower edge of what was actually measured still works.
  // These fail if someone later raises a floor above what was measured.

  @Container
  private static final OracleContainer ORACLE_11_2 =
      // The oracle-xe module's org.testcontainers.containers.OracleContainer, not oracle-free's
      // org.testcontainers.oracle.OracleContainer — 11g XE predates pluggable databases, so the
      // oracle-free module's PDB-shaped wait strategy and JDBC URL do not apply here.
      new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:11.2.0.2-slim"));

  @Test
  void oracle11Point2SkipsLockedRowsAndSaysSo() throws SQLException {
    // OracleContainer#getJdbcUrl() produces jdbc:oracle:thin:@host:port/xepdb1, a pluggable-
    // database service name that does not exist on 11g — there is no PDB, only the SID "xe". That
    // URL fails to connect; the working URL uses a colon before the SID, not a slash:
    // jdbc:oracle:thin:@host:port:xe. Two failed probe runs went into learning this before it was
    // written down here.
    var url =
        "jdbc:oracle:thin:@"
            + ORACLE_11_2.getHost()
            + ":"
            + ORACLE_11_2.getMappedPort(1521)
            + ":xe";

    try (var first =
            Drivers.connect(
                new oracle.jdbc.OracleDriver(),
                url,
                ORACLE_11_2.getUsername(),
                ORACLE_11_2.getPassword());
        var second =
            Drivers.connect(
                new oracle.jdbc.OracleDriver(),
                url,
                ORACLE_11_2.getUsername(),
                ORACLE_11_2.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // 11.2.0.2 is the lowest Oracle version measured; it genuinely skips. The floor is 11 —
      // see Platform.Oracle.
      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  private static final int YUGABYTE_YSQL_PORT = 5433;

  @Container
  private static final YugabyteDBYSQLContainer YUGABYTE_2_16 =
      new YugabyteDBYSQLContainer(DockerImageName.parse("yugabytedb/yugabyte:2.16.9.0-b67"))
          .withDatabaseName("yugabyte")
          .withUsername("yugabyte")
          .withPassword("yugabyte");

  @Test
  void yugabyte2Point16SkipsLockedRowsAndSaysSo() throws SQLException {
    // Reached through pgjdbc on the mapped YSQL port, exactly as YugabyteDbIT does and for the
    // same reason: the YugabyteDB driver also claims jdbc:postgresql: URLs, so an explicit pgjdbc
    // Driver instance (never DriverManager) is required to measure the right pairing.
    var url =
        "jdbc:postgresql://"
            + YUGABYTE_2_16.getHost()
            + ":"
            + YUGABYTE_2_16.getMappedPort(YUGABYTE_YSQL_PORT)
            + "/yugabyte";

    try (var first =
            Drivers.connect(
                new org.postgresql.Driver(),
                url,
                YUGABYTE_2_16.getUsername(),
                YUGABYTE_2_16.getPassword());
        var second =
            Drivers.connect(
                new org.postgresql.Driver(),
                url,
                YUGABYTE_2_16.getUsername(),
                YUGABYTE_2_16.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // 2.16.9.0 is the lowest YugabyteDB version measured; it genuinely skips. The floor is
      // 2.16 — see Platform.YugabyteDB.
      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }
}
