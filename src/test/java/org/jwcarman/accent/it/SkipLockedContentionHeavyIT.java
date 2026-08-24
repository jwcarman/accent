package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
  private static final OracleContainer ORACLE =
      new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"));

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

      var skips = SkipLockedContention.skipsLockedRows(first, second);
      var platform = Accent.of(second);

      assertThat(skips).isTrue();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved")
          .isEqualTo(skips);
    }
  }

  @Container
  private static final Db2Container DB2 =
      new Db2Container(DockerImageName.parse("icr.io/db2_community/db2:12.1.0.0")).acceptLicense();

  @Test
  void db2DoesNotSkipLockedRowsDespiteAcceptingTheSyntax() throws SQLException {
    try (var first =
            Drivers.connect(
                new com.ibm.db2.jcc.DB2Driver(), DB2.getJdbcUrl(), DB2.getUsername(), DB2.getPassword());
        var second =
            Drivers.connect(
                new com.ibm.db2.jcc.DB2Driver(),
                DB2.getJdbcUrl(),
                DB2.getUsername(),
                DB2.getPassword())) {

      var skips = SkipLockedContention.skipsLockedRows(first, second);
      var platform = Accent.of(second);

      // Db2 accepts the SKIP LOCKED syntax but blocks anyway — parsing without semantics, the
      // exact trap this predicate exists to catch. See the javadoc on Platform.Db2.
      assertThat(skips).isFalse();
      assertThat(platform.supportsSkipLocked())
          .as("the arm must report what contention actually proved")
          .isEqualTo(skips);
    }
  }
}
