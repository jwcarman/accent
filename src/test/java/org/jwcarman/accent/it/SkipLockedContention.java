package org.jwcarman.accent.it;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Determines whether a database really has skip-locked semantics.
 *
 * <p>Parsing is not semantics. Every current server accent tests accepts {@code FOR UPDATE SKIP
 * LOCKED} as syntax, including engines whose locking behaviour differs. The only test that
 * distinguishes them holds a lock on one connection and asks a second whether it skips or blocks.
 */
final class SkipLockedContention {

  private static final String TABLE = "accent_skip_locked_probe";
  private static final int BLOCK_TIMEOUT_SECONDS = 5;

  private SkipLockedContention() {}

  /**
   * Locks one of two rows on {@code first}, then asks {@code second} to claim with skip-locked.
   *
   * @param first the connection that holds a lock
   * @param second the connection that attempts to skip it
   * @return true if the second connection returned only the unlocked row rather than blocking
   * @throws SQLException if the fixture could not be created
   */
  static boolean skipsLockedRows(Connection first, Connection second) throws SQLException {
    prepare(first);
    first.setAutoCommit(false);
    second.setAutoCommit(false);
    try {
      // Connection one locks row 1 and holds it.
      try (Statement statement = first.createStatement()) {
        statement.executeQuery("SELECT id FROM " + TABLE + " WHERE id = 1 FOR UPDATE").close();
      }

      // Connection two asks for everything, skipping whatever is locked.
      try (Statement statement = second.createStatement()) {
        statement.setQueryTimeout(BLOCK_TIMEOUT_SECONDS);
        try (var rows =
            statement.executeQuery(
                "SELECT id FROM " + TABLE + " ORDER BY id FOR UPDATE SKIP LOCKED")) {
          var returned = new ArrayList<Integer>();
          while (rows.next()) {
            returned.add(rows.getInt(1));
          }
          // Genuine skip-locked returns row 2 only. Blocking would have timed out above;
          // returning both rows means the clause was accepted and ignored.
          return returned.equals(List.of(2));
        }
      } catch (SQLException e) {
        // Timed out waiting on the lock, or the clause was rejected. Either way: no.
        return false;
      }
    } finally {
      rollbackQuietly(first);
      rollbackQuietly(second);
    }
  }

  private static void prepare(Connection connection) throws SQLException {
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      try {
        statement.execute("DROP TABLE " + TABLE);
      } catch (SQLException ignored) {
        // Absent is the normal case on a fresh container.
      }
      statement.execute("CREATE TABLE " + TABLE + " (id INTEGER NOT NULL PRIMARY KEY)");
      statement.execute("INSERT INTO " + TABLE + " (id) VALUES (1)");
      statement.execute("INSERT INTO " + TABLE + " (id) VALUES (2)");
    }
  }

  private static void rollbackQuietly(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException ignored) {
      // Rollback failure is not the fact under measurement.
    }
  }
}
