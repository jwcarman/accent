package org.jwcarman.accent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.jwcarman.accent.Platform.CockroachDB;
import org.jwcarman.accent.Platform.MySQL;
import org.jwcarman.accent.Platform.PostgreSQL;
import org.junit.jupiter.api.Test;

class AccentTest {

  /** Builds mock metadata whose connection answers SELECT version() with the given text. */
  private static DatabaseMetaData metaData(
      String productName, String productVersion, int major, int minor, String versionQuery)
      throws SQLException {
    var metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn(productName);
    when(metaData.getDatabaseProductVersion()).thenReturn(productVersion);
    when(metaData.getDatabaseMajorVersion()).thenReturn(major);
    when(metaData.getDatabaseMinorVersion()).thenReturn(minor);

    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    var resultSet = mock(ResultSet.class);
    when(metaData.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(versionQuery != null);
    when(resultSet.getString(1)).thenReturn(versionQuery);
    return metaData;
  }

  @Test
  void identifiesAPlatformFromMetadata() throws SQLException {
    var metaData =
        metaData(ObservedStrings.MYSQL_NAME, ObservedStrings.MYSQL_VERSION, 8, 4, null);

    var platform = Accent.of(metaData);

    assertThat(platform).isInstanceOf(MySQL.class);
    assertThat(platform.majorVersion()).isEqualTo(8);
    assertThat(platform.minorVersion()).isEqualTo(4);
  }

  @Test
  void queriesTheServerToSeparateCockroachFromPostgres() throws SQLException {
    var metaData =
        metaData(
            ObservedStrings.COCKROACH_NAME,
            ObservedStrings.COCKROACH_VERSION,
            13,
            0,
            ObservedStrings.COCKROACH_VERSION_QUERY);

    assertThat(Accent.of(metaData)).isInstanceOf(CockroachDB.class);
  }

  @Test
  void identifiesPostgresProper() throws SQLException {
    var metaData =
        metaData(
            ObservedStrings.POSTGRES_NAME,
            ObservedStrings.POSTGRES_VERSION,
            17,
            10,
            ObservedStrings.POSTGRES_VERSION_QUERY);

    assertThat(Accent.of(metaData)).isInstanceOf(PostgreSQL.class);
  }

  @Test
  void doesNotQueryDatabasesThatMetadataAlreadyIdentifies() throws SQLException {
    var metaData = metaData(ObservedStrings.MYSQL_NAME, ObservedStrings.MYSQL_VERSION, 8, 4, null);

    Accent.of(metaData);

    // No round trip for families that do not need one.
    verify(metaData, never()).getConnection();
  }

  @Test
  void readsMetadataFromAConnection() throws SQLException {
    var metaData = metaData(ObservedStrings.H2_NAME, ObservedStrings.H2_VERSION, 2, 3, null);
    var connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);

    assertThat(Accent.of(connection)).isInstanceOf(Platform.H2.class);
  }

  @Test
  void opensAndClosesAConnectionFromADataSource() throws SQLException {
    var metaData = metaData(ObservedStrings.H2_NAME, ObservedStrings.H2_VERSION, 2, 3, null);
    var connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);
    var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenReturn(connection);

    assertThat(Accent.of(dataSource)).isInstanceOf(Platform.H2.class);
    verify(connection).close();
  }

  @Test
  void doesNotCloseACallerSuppliedConnection() throws SQLException {
    var metaData = metaData(ObservedStrings.H2_NAME, ObservedStrings.H2_VERSION, 2, 3, null);
    var connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);

    Accent.of(connection);

    verify(connection, never()).close();
  }

  @Test
  void raisesAccentExceptionWhenItCannotAsk() throws SQLException {
    var dataSource = mock(DataSource.class);
    var cause = new SQLException("connection refused");
    when(dataSource.getConnection()).thenThrow(cause);

    // "could not ask" must never collapse into Unknown, which means "asked, did not recognise".
    assertThatExceptionOfType(AccentException.class)
        .isThrownBy(() -> Accent.of(dataSource))
        .withCause(cause);
  }

  @Test
  void raisesAccentExceptionWhenTheVersionQueryFails() throws SQLException {
    var metaData =
        metaData(
            ObservedStrings.POSTGRES_NAME,
            ObservedStrings.POSTGRES_VERSION,
            17,
            10,
            ObservedStrings.POSTGRES_VERSION_QUERY);
    var cause = new SQLException("permission denied");
    when(metaData.getConnection().createStatement()).thenThrow(cause);

    assertThatExceptionOfType(AccentException.class)
        .isThrownBy(() -> Accent.of(metaData))
        .withCause(cause);
  }
}
