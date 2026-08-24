package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** MySQL proper, which must not be swept up by the MariaDB disambiguator. */
@Testcontainers
class MySqlIT {

  @Container
  private static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

  @Test
  void isDetected() throws SQLException {
    try (var connection =
        Drivers.connect(
            new com.mysql.cj.jdbc.Driver(),
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.MySQL.class);
      assertThat(platform).isNotInstanceOf(Platform.MariaDB.class);
      assertThat(platform.productVersion()).doesNotContainIgnoringCase("mariadb");
    }
  }
}
