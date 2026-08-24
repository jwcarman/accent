package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL proper. The baseline the impostors must not be confused with, and vice versa. */
@Testcontainers
class PostgreSqlIT {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

  @Test
  void isDetected() throws SQLException {
    try (var connection =
        Drivers.connect(
            new org.postgresql.Driver(),
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.PostgreSQL.class);
      assertThat(platform.productName()).isEqualTo("PostgreSQL");
      assertThat(platform.majorVersion()).isEqualTo(17);
    }
  }
}
