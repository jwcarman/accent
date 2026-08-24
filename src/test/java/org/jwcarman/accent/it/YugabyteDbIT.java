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
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.testcontainers.containers.YugabyteDBYSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * YugabyteDB reached through pgjdbc.
 *
 * <p>Note the deliberate URL construction. {@code YugabyteDBYSQLContainer#getJdbcUrl()} returns a
 * {@code jdbc:yugabytedb:} URL, and the YugabyteDB driver also claims {@code jdbc:postgresql:} URLs
 * — so this test builds the pgjdbc URL by hand and passes an explicit pgjdbc driver. Anything less
 * measures the wrong pairing.
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
