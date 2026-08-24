package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.YugabyteDBYSQLContainer;

/**
 * YugabyteDB reached through pgjdbc.
 *
 * <p>Note the deliberate URL construction. {@code YugabyteDBYSQLContainer#getJdbcUrl()} returns a
 * {@code jdbc:yugabytedb:} URL, and the YugabyteDB driver also claims {@code jdbc:postgresql:}
 * URLs — so this test builds the pgjdbc URL by hand and passes an explicit pgjdbc driver. Anything
 * less measures the wrong pairing.
 */
@Testcontainers
class YugabyteDbIT {

  private static final int YSQL_PORT = 5433;

  @Container
  private static final YugabyteDBYSQLContainer YUGABYTE =
      new YugabyteDBYSQLContainer(DockerImageName.parse("yugabytedb/yugabyte:2024.1.0.0-b129"))
          .withDatabaseName("yugabyte")
          .withUsername("yugabyte")
          .withPassword("yugabyte");

  @Test
  void isNotMistakenForPostgres() throws SQLException {
    var url =
        "jdbc:postgresql://"
            + YUGABYTE.getHost()
            + ":"
            + YUGABYTE.getMappedPort(YSQL_PORT)
            + "/yugabyte";

    try (var connection =
        Drivers.connect(
            new org.postgresql.Driver(), url, YUGABYTE.getUsername(), YUGABYTE.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.YugabyteDB.class);
      assertThat(platform).isNotInstanceOf(Platform.PostgreSQL.class);
      assertThat(platform.productName()).isEqualTo("PostgreSQL");
    }
  }
}
