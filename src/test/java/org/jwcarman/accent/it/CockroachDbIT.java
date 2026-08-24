package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * CockroachDB reached through pgjdbc.
 *
 * <p>The flagship case. This server reports product name {@code PostgreSQL} and a bare PostgreSQL
 * version number; nothing in the four identity fields separates it from a genuine PostgreSQL 13
 * server. If this test passes, accent is doing something a product-name check cannot.
 */
@Testcontainers
class CockroachDbIT {

  @Container
  private static final CockroachContainer COCKROACH =
      new CockroachContainer(DockerImageName.parse("cockroachdb/cockroach:latest-v24.1"));

  @Test
  void isNotMistakenForPostgres() throws SQLException {
    try (var connection =
        Drivers.connect(
            new org.postgresql.Driver(),
            COCKROACH.getJdbcUrl(),
            COCKROACH.getUsername(),
            COCKROACH.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.CockroachDB.class);
      assertThat(platform).isNotInstanceOf(Platform.PostgreSQL.class);
    }
  }

  @Test
  void stillReportsTheRawPostgresIdentityItClaims() throws SQLException {
    try (var connection =
        Drivers.connect(
            new org.postgresql.Driver(),
            COCKROACH.getJdbcUrl(),
            COCKROACH.getUsername(),
            COCKROACH.getPassword())) {

      var platform = Accent.of(connection);

      // Version is raw driver output, always. It describes the impersonation.
      assertThat(platform.productName()).isEqualTo("PostgreSQL");
      assertThat(platform.productVersion()).doesNotContainIgnoringCase("cockroach");
    }
  }
}
