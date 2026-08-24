package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Accent;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.YugabyteDBYSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that {@code supportsSkipLocked()} tells the truth, by contention rather than by parsing.
 *
 * <p>Each engine gets two connections: one holds a row lock, the other must skip it. Whatever this
 * test observes is what the corresponding {@link org.jwcarman.accent.Platform} arm must report.
 */
@Testcontainers
class SkipLockedContentionIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

  @Test
  void postgresSkipsLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new org.postgresql.Driver(),
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        var second =
            Drivers.connect(
                new org.postgresql.Driver(),
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

  @Test
  void mysqlSkipsLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new com.mysql.cj.jdbc.Driver(),
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword());
        var second =
            Drivers.connect(
                new com.mysql.cj.jdbc.Driver(),
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final MariaDBContainer<?> MARIADB =
      new MariaDBContainer<>(DockerImageName.parse("mariadb:11.4"));

  @Test
  void mariadbSkipsLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new org.mariadb.jdbc.Driver(),
                MARIADB.getJdbcUrl(),
                MARIADB.getUsername(),
                MARIADB.getPassword());
        var second =
            Drivers.connect(
                new org.mariadb.jdbc.Driver(),
                MARIADB.getJdbcUrl(),
                MARIADB.getUsername(),
                MARIADB.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final CockroachContainer COCKROACH =
      new CockroachContainer(DockerImageName.parse("cockroachdb/cockroach:latest-v24.1"));

  @Test
  void cockroachSkipsLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new org.postgresql.Driver(),
                COCKROACH.getJdbcUrl(),
                COCKROACH.getUsername(),
                COCKROACH.getPassword());
        var second =
            Drivers.connect(
                new org.postgresql.Driver(),
                COCKROACH.getJdbcUrl(),
                COCKROACH.getUsername(),
                COCKROACH.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  private static final int YSQL_PORT = 5433;

  @Container
  private static final YugabyteDBYSQLContainer YUGABYTE =
      new YugabyteDBYSQLContainer(DockerImageName.parse("yugabytedb/yugabyte:2024.1.0.0-b129"))
          .withDatabaseName("yugabyte")
          .withUsername("yugabyte")
          .withPassword("yugabyte");

  @Test
  void yugabyteSkipsLockedRowsAndSaysSo() throws SQLException {
    var url =
        "jdbc:postgresql://"
            + YUGABYTE.getHost()
            + ":"
            + YUGABYTE.getMappedPort(YSQL_PORT)
            + "/yugabyte";

    try (var first =
            Drivers.connect(
                new org.postgresql.Driver(), url, YUGABYTE.getUsername(), YUGABYTE.getPassword());
        var second =
            Drivers.connect(
                new org.postgresql.Driver(), url, YUGABYTE.getUsername(), YUGABYTE.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final MSSQLServerContainer<?> SQLSERVER =
      new MSSQLServerContainer<>(
              DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
          .acceptLicense();

  @Test
  void sqlServerDoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
                SQLSERVER.getJdbcUrl(),
                SQLSERVER.getUsername(),
                SQLSERVER.getPassword());
        var second =
            Drivers.connect(
                new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
                SQLSERVER.getJdbcUrl(),
                SQLSERVER.getUsername(),
                SQLSERVER.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // SQL Server has no FOR UPDATE SKIP LOCKED clause. Plain FOR UPDATE is itself rejected
      // outside a cursor, so this must be false — see the javadoc on Platform.SqlServer.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Test
  void h2SkipsLockedRowsAndSaysSo() throws SQLException {
    var url = "jdbc:h2:mem:accent_skiplocked_h2;DB_CLOSE_DELAY=-1";
    try (var first = Drivers.connect(new org.h2.Driver(), url, null, null);
        var second = Drivers.connect(new org.h2.Driver(), url, null, null)) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // H2 parses the clause; this is the test that settles whether it genuinely skips.
      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Test
  void hsqldbDoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    var url = "jdbc:hsqldb:mem:accent_skiplocked_hsqldb";
    try (var first = Drivers.connect(new org.hsqldb.jdbc.JDBCDriver(), url, "SA", "");
        var second = Drivers.connect(new org.hsqldb.jdbc.JDBCDriver(), url, "SA", "")) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // HSQLDB rejects the SKIP LOCKED clause outright.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Test
  void sqliteDoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    var url = "jdbc:sqlite:file::memory:?cache=shared";
    try (var first = Drivers.connect(new org.sqlite.JDBC(), url, null, null);
        var second = Drivers.connect(new org.sqlite.JDBC(), url, null, null)) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // SQLite has no row-level locking model; even plain FOR UPDATE is a syntax error.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Test
  void derbyDoesNotSkipLockedRowsAndSaysSo() throws SQLException {
    var url = "jdbc:derby:memory:accent_skiplocked_derby;create=true";
    try (var first = Drivers.connect(new org.apache.derby.jdbc.EmbeddedDriver(), url, null, null);
        var second =
            Drivers.connect(
                new org.apache.derby.jdbc.EmbeddedDriver(),
                "jdbc:derby:memory:accent_skiplocked_derby",
                null,
                null)) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // Derby rejects the SKIP LOCKED clause outright.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }
}
