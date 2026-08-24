package org.jwcarman.accent.it;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Connects through an explicitly chosen JDBC driver.
 *
 * <p>Never use {@link java.sql.DriverManager} in this suite. {@code com.yugabyte.Driver} also
 * accepts {@code jdbc:postgresql:} URLs, so with both jars on the classpath {@code DriverManager}
 * may route a pgjdbc URL to the YugabyteDB driver. The whole point of this matrix is that the
 * driver/server pairing is exact; letting URL routing choose defeats it silently.
 */
final class Drivers {

  private Drivers() {}

  /**
   * Opens a connection using the given driver and nothing else.
   *
   * @param driver the driver to use
   * @param url the JDBC URL
   * @param user the username
   * @param password the password
   * @return an open connection
   * @throws SQLException if the connection could not be opened
   */
  static Connection connect(Driver driver, String url, String user, String password)
      throws SQLException {
    var properties = new Properties();
    // Properties.setProperty throws NullPointerException on a null value. Some Testcontainers
    // containers report a null or empty password (e.g. CockroachDB runs in insecure mode), so
    // each property is set only when its value is non-null rather than letting that surface as
    // a baffling NPE unrelated to the actual connection attempt.
    if (user != null) {
      properties.setProperty("user", user);
    }
    if (password != null) {
      properties.setProperty("password", password);
    }
    var connection = driver.connect(url, properties);
    if (connection == null) {
      throw new SQLException("driver " + driver.getClass().getName() + " declined the URL " + url);
    }
    return connection;
  }
}
