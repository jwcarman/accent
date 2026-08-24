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
    return connect(driver, url, user, password, new Properties());
  }

  /**
   * Opens a connection using the given driver, with additional driver-specific properties beyond
   * user/password.
   *
   * <p>For a case like Oracle's {@code oracle.jdbc.timezoneAsRegion}, this keeps the workaround
   * scoped to the one test that needs it, rather than a JVM system property or default that would
   * silently change behaviour for every other test in the suite.
   *
   * @param driver the driver to use
   * @param url the JDBC URL
   * @param user the username
   * @param password the password
   * @param extraProperties additional connection properties, merged in alongside user/password
   * @return an open connection
   * @throws SQLException if the connection could not be opened
   */
  static Connection connect(
      Driver driver, String url, String user, String password, Properties extraProperties)
      throws SQLException {
    var properties = new Properties();
    properties.putAll(extraProperties);
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
