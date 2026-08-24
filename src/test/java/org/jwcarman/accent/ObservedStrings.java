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
package org.jwcarman.accent;

/**
 * Driver output observed against real servers on 2026-08-24, transcribed verbatim from
 * docs/observed-strings.md.
 *
 * <p>These are measurements, not guesses. When an integration test starts failing after a version
 * bump, the observed string has changed and both the heuristic and the constant here must be
 * updated together.
 */
final class ObservedStrings {

  // --- PostgreSQL family, all reached through pgjdbc 42.7.12 ---

  static final String POSTGRES_NAME = "PostgreSQL";
  static final String POSTGRES_VERSION = "17.10 (Debian 17.10-1.pgdg13+1)";
  static final String POSTGRES_VERSION_QUERY =
      "PostgreSQL 17.10 (Debian 17.10-1.pgdg13+1) on aarch64-unknown-linux-gnu,"
          + " compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit";

  /** CockroachDB reports PostgreSQL with a bare version. Only version() gives it away. */
  static final String COCKROACH_NAME = "PostgreSQL";

  static final String COCKROACH_VERSION = "13.0.0";
  static final String COCKROACH_VERSION_QUERY =
      "CockroachDB CCL v24.1.32 (aarch64-unknown-linux-gnu, built 2026/07/22 12:34:17,"
          + " go1.22.12 X:nocoverageredesign)";

  static final String YUGABYTE_NAME = "PostgreSQL";
  static final String YUGABYTE_VERSION = "11.2-YB-2024.1.0.0-b0";
  static final String YUGABYTE_VERSION_QUERY =
      "PostgreSQL 11.2-YB-2024.1.0.0-b0 on aarch64-unknown-linux-gnu, compiled by clang version"
          + " 17.0.6 (https://github.com/yugabyte/llvm-project.git"
          + " 9b881774e40024e901fc6f3d313607b071c08631), 64-bit";

  // --- MySQL family ---

  static final String MYSQL_NAME = "MySQL";
  static final String MYSQL_VERSION = "8.4.11";

  /** MariaDB through mariadb-java-client 3.5.2. */
  static final String MARIADB_NAME = "MariaDB";

  /** MariaDB through mysql-connector-j 9.2.0 — the impostor. */
  static final String MARIADB_VIA_MYSQL_NAME = "MySQL";

  /** Identical through both drivers; the only thing naming MariaDB. */
  static final String MARIADB_VERSION = "11.4.12-MariaDB-ubu2404";

  // --- Everything else ---

  static final String SQLSERVER_NAME = "Microsoft SQL Server";
  static final String SQLSERVER_VERSION = "16.00.4265";

  static final String ORACLE_NAME = "Oracle";

  /** Two lines, verbatim. Do not collapse. */
  static final String ORACLE_VERSION =
      """
      Oracle AI Database 26ai Free Release 23.26.2.0.0 - Develop, Learn, and Run for Free
      Version 23.26.2.0.0""";

  /** The suffix is host-architecture specific; only the DB2 prefix is stable. */
  static final String DB2_NAME = "DB2/LINUXX8664";

  /** A build identifier, not a dotted version. Nothing parses out of it. */
  static final String DB2_VERSION = "SQL120100";

  static final String H2_NAME = "H2";
  static final String H2_VERSION = "2.3.232 (2024-08-11)";

  static final String HSQLDB_NAME = "HSQL Database Engine";
  static final String HSQLDB_VERSION = "2.7.4";

  static final String SQLITE_NAME = "SQLite";
  static final String SQLITE_VERSION = "3.47.1";

  static final String DERBY_NAME = "Apache Derby";
  static final String DERBY_VERSION = "10.17.1.0 - (1913217)";

  private ObservedStrings() {}
}
