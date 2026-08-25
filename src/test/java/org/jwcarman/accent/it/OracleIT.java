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
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Oracle. Slow image, so tagged {@code heavy} and excluded from a default {@code mvn verify}.
 *
 * <p>Oracle's product version spans two lines and names a marketing release ({@code Oracle AI
 * Database 26ai}) that disagrees with its release number ({@code 23.26.2.0.0}). Trust the integer
 * accessors, not the prose.
 */
@Tag("heavy")
@Testcontainers
class OracleIT {

  @Container
  // Oracle images are slow to become ready and Testcontainers' default startup timeout is 60
  // seconds. On a loaded CI runner this container has taken 15 seconds on one run and timed out
  // past 62 on another, for the same image and the same commit — a docs-only change failed the
  // build. The generous timeout below trades a slower worst case for a build that fails only when
  // something is actually wrong.
  private static final OracleContainer ORACLE =
      new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
          .withStartupTimeout(Duration.ofMinutes(5));

  @Test
  void isDetected() throws SQLException {
    try (var connection =
        Drivers.connect(
            new oracle.jdbc.OracleDriver(),
            ORACLE.getJdbcUrl(),
            ORACLE.getUsername(),
            ORACLE.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.Oracle.class);
      assertThat(platform.productName()).isEqualTo("Oracle");
    }
  }

  @Test
  void isDetectedDespiteAMultiLineProductVersion() throws SQLException {
    try (var connection =
        Drivers.connect(
            new oracle.jdbc.OracleDriver(),
            ORACLE.getJdbcUrl(),
            ORACLE.getUsername(),
            ORACLE.getPassword())) {

      var platform = Accent.of(connection);

      // Guards against anyone "tidying" the heuristic into an anchored regex.
      assertThat(platform.productVersion()).contains("\n");
      assertThat(platform.majorVersion()).isEqualTo(23);
    }
  }
}
