package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that {@code supportsSkipLocked()} tells the truth, by contention rather than by
 * parsing.
 *
 * <p>Each engine gets two connections: one holds a row lock, the other must skip it. Whatever
 * this test observes is what the corresponding {@link org.jwcarman.accent.Platform} arm must
 * report.
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

      var skips = SkipLockedContention.skipsLockedRows(first, second);
      var platform = Accent.of(second);

      assertThat(skips).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved")
          .isEqualTo(skips);
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

      var skips = SkipLockedContention.skipsLockedRows(first, second);
      var platform = Accent.of(second);

      assertThat(skips).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved")
          .isEqualTo(skips);
    }
  }
}
