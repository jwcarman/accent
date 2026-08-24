package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Microsoft SQL Server. The image is amd64-only and runs under emulation on Apple Silicon. */
@Testcontainers
class SqlServerIT {

  @Container
  private static final MSSQLServerContainer<?> SQLSERVER =
      new MSSQLServerContainer<>(
              DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
          .acceptLicense();

  @Test
  void isDetected() throws SQLException {
    try (var connection =
        Drivers.connect(
            new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            SQLSERVER.getJdbcUrl(),
            SQLSERVER.getUsername(),
            SQLSERVER.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.SqlServer.class);
      assertThat(platform.productName()).isEqualTo("Microsoft SQL Server");
    }
  }
}
