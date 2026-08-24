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
  private static final int MAX_DETAIL_LENGTH = 160;

  private SkipLockedContention() {}

  /**
   * The result of one contention probe, with the reason attached.
   *
   * <p>{@code detail} exists so a future run — against a new driver version, a new server version,
   * or a different environment — leaves a record of *why* the answer came out the way it did, not
   * just what the answer was. A bare boolean cannot distinguish "genuinely blocked waiting on the
   * lock" from "the driver rejected the statement for an unrelated reason," and that distinction is
   * exactly what makes a result like Db2's trustworthy or not.
   *
   * @param skips true if the second connection returned only the unlocked row rather than blocking
   * @param detail a short, human-readable account of what was actually observed
   */
  record Outcome(boolean skips, String detail) {}

  /**
   * Locks one of two rows on {@code first}, then asks {@code second} to claim with skip-locked,
   * recording what actually happened.
   *
   * @param first the connection that holds a lock
   * @param second the connection that attempts to skip it
   * @return the outcome: whether it skipped, and why
   * @throws SQLException if the fixture could not be created
   */
  static Outcome probe(Connection first, Connection second) throws SQLException {
    prepare(first);
    first.setAutoCommit(false);
    second.setAutoCommit(false);
    try {
      // Connection one locks row 1 and holds it. On an engine that rejects plain FOR UPDATE
      // outside a cursor (SQL Server) or does not support row locking at all (SQLite), this
      // itself fails — which is a legitimate "no" for a predicate scoped to FOR UPDATE SKIP
      // LOCKED, not a fixture defect.
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
          if (returned.equals(List.of(2))) {
            return new Outcome(true, "skipped: returned " + returned);
          }
          return new Outcome(
              false, "did not skip: returned " + returned + " — clause accepted and ignored");
        }
      }
    } catch (SQLException e) {
      // Timed out waiting on the lock, the SKIP LOCKED clause was rejected, or even plain
      // FOR UPDATE was rejected. The exception itself is recorded rather than swallowed, so a
      // future run can tell "blocked" apart from "the driver failed for an unrelated reason."
      return new Outcome(false, "did not skip: " + describe(e));
    } finally {
      rollbackQuietly(first);
      rollbackQuietly(second);
    }
  }

  private static String describe(SQLException e) {
    var message = e.getMessage();
    var detail = e.getClass().getSimpleName() + ": " + (message == null ? "(no message)" : message);
    detail = detail.replace('\n', ' ').replace('\r', ' ');
    return detail.length() > MAX_DETAIL_LENGTH
        ? detail.substring(0, MAX_DETAIL_LENGTH) + "..."
        : detail;
  }

  private static void prepare(Connection connection) throws SQLException {
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      try {
        statement.execute("DROP TABLE " + TABLE);
      } catch (SQLException _) {
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
    } catch (SQLException _) {
      // Rollback failure is not the fact under measurement.
    }
  }
}
