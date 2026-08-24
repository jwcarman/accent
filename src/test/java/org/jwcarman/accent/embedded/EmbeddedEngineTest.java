package org.jwcarman.accent.embedded;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;

/**
 * Detection against real in-process engines. No Docker, so these live in the fast suite and are
 * exhaustive rather than sampled.
 *
 * <p>These are the tests that catch the day an embedded driver changes what it reports.
 */
class EmbeddedEngineTest {

  private static Platform detect(String url) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url)) {
      return Accent.of(connection);
    }
  }

  @Test
  void detectsH2() throws SQLException {
    var platform = detect("jdbc:h2:mem:accent_h2;DB_CLOSE_DELAY=-1");

    assertThat(platform).isInstanceOf(Platform.H2.class);
    assertThat(platform.productName()).isEqualTo("H2");
  }

  @Test
  void detectsHsqldb() throws SQLException {
    var platform = detect("jdbc:hsqldb:mem:accent_hsqldb");

    assertThat(platform).isInstanceOf(Platform.HSQLDB.class);
    assertThat(platform.productName()).isEqualTo("HSQL Database Engine");
  }

  @Test
  void detectsSqlite() throws SQLException {
    var platform = detect("jdbc:sqlite::memory:");

    assertThat(platform).isInstanceOf(Platform.SQLite.class);
    assertThat(platform.productName()).isEqualTo("SQLite");
  }

  @Test
  void detectsDerby() throws SQLException {
    var platform = detect("jdbc:derby:memory:accent_derby;create=true");

    assertThat(platform).isInstanceOf(Platform.Derby.class);
    assertThat(platform.productName()).isEqualTo("Apache Derby");
  }

  @Test
  void noEmbeddedEngineIncursAVersionQuery() throws SQLException {
    // None of these is in the PostgreSQL family, so none should pay for a round trip.
    // Asserted indirectly: detection succeeds on SQLite, whose driver rejects SELECT version().
    assertThat(detect("jdbc:sqlite::memory:")).isInstanceOf(Platform.SQLite.class);
  }
}
