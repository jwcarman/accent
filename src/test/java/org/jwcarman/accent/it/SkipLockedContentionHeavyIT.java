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
import org.testcontainers.containers.Db2Container;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contention tests for the slow, licence-gated engines. Tagged {@code heavy} and excluded from a
 * default {@code mvn verify}; run with {@code -Dexcluded.test.groups=}.
 *
 * <p>Same method as {@link SkipLockedContentionIT}: one connection holds a row lock, a second
 * attempts to skip it, and whatever is observed is what the corresponding {@link
 * org.jwcarman.accent.Platform} arm must report.
 */
@Tag("heavy")
@Testcontainers
class SkipLockedContentionHeavyIT {

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
  void oracleSkipsLockedRowsAndSaysSo() throws SQLException {
    try (var first =
            Drivers.connect(
                new oracle.jdbc.OracleDriver(),
                ORACLE.getJdbcUrl(),
                ORACLE.getUsername(),
                ORACLE.getPassword());
        var second =
            Drivers.connect(
                new oracle.jdbc.OracleDriver(),
                ORACLE.getJdbcUrl(),
                ORACLE.getUsername(),
                ORACLE.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      assertThat(outcome.skips()).as(outcome.detail()).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }

  @Container
  private static final Db2Container DB2 =
      new Db2Container(DockerImageName.parse("icr.io/db2_community/db2:12.1.0.0")).acceptLicense();

  @Test
  void db2DoesNotSkipLockedRowsDespiteAcceptingTheSyntax() throws SQLException {
    try (var first =
            Drivers.connect(
                new com.ibm.db2.jcc.DB2Driver(),
                DB2.getJdbcUrl(),
                DB2.getUsername(),
                DB2.getPassword());
        var second =
            Drivers.connect(
                new com.ibm.db2.jcc.DB2Driver(),
                DB2.getJdbcUrl(),
                DB2.getUsername(),
                DB2.getPassword())) {

      var outcome = SkipLockedContention.probe(first, second);
      var platform = Accent.of(second);
      System.out.println("contention detail: " + outcome.detail());

      // Db2 accepts the SKIP LOCKED syntax but ignores it — the second connection returns
      // both rows, including the one the first still holds locked, instead of skipping it or
      // blocking. Parsing without semantics: the exact trap this predicate exists to catch.
      // See the javadoc on Platform.Db2.
      assertThat(outcome.skips()).as(outcome.detail()).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved: %s", outcome.detail())
          .isEqualTo(outcome.skips());
    }
  }
}
