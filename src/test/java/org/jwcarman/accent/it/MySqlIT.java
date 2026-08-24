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
