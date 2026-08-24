package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * One MariaDB server, reached through both drivers.
 *
 * <p>Through {@code mariadb-java-client} it reports {@code MariaDB}; through
 * {@code mysql-connector-j} it reports {@code MySQL}. Same server, two identities, depending on a
 * dependency choice made elsewhere. Both must resolve to {@link Platform.MariaDB}.
 */
@Testcontainers
class MariaDbViaMySqlDriverIT {

  private static final int MARIADB_PORT = 3306;

  @Container
  private static final MariaDBContainer<?> MARIADB =
      new MariaDBContainer<>(DockerImageName.parse("mariadb:11.4"));

  @Test
  void isDetectedThroughItsOwnDriver() throws SQLException {
    try (var connection =
        Drivers.connect(
            new org.mariadb.jdbc.Driver(),
            MARIADB.getJdbcUrl(),
            MARIADB.getUsername(),
            MARIADB.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.MariaDB.class);
      assertThat(platform.productName()).isEqualTo("MariaDB");
    }
  }

  @Test
  void isNotMistakenForMySqlThroughTheMySqlDriver() throws SQLException {
    var url =
        "jdbc:mysql://"
            + MARIADB.getHost()
            + ":"
            + MARIADB.getMappedPort(MARIADB_PORT)
            + "/"
            + MARIADB.getDatabaseName()
            + "?allowPublicKeyRetrieval=true&useSSL=false";

    try (var connection =
        Drivers.connect(
            new com.mysql.cj.jdbc.Driver(), url, MARIADB.getUsername(), MARIADB.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.MariaDB.class);
      assertThat(platform).isNotInstanceOf(Platform.MySQL.class);
      // The product name really does say MySQL. Only the version string tells the truth.
      assertThat(platform.productName()).isEqualTo("MySQL");
      assertThat(platform.productVersion()).containsIgnoringCase("mariadb");
    }
  }
}
