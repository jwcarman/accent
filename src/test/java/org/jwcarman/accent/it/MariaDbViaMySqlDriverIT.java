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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * One MariaDB server, reached through both drivers.
 *
 * <p>Through {@code mariadb-java-client} it reports {@code MariaDB}; through {@code
 * mysql-connector-j} it reports {@code MySQL}. Same server, two identities, depending on a
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
