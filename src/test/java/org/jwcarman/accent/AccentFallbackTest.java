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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.jwcarman.accent.Platform.MySQL;
import org.jwcarman.accent.Platform.PostgreSQL;
import org.jwcarman.accent.Platform.Unknown;

class AccentFallbackTest {

  private static DatabaseMetaData metaData(String productName, String productVersion)
      throws SQLException {
    var metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn(productName);
    when(metaData.getDatabaseProductVersion()).thenReturn(productVersion);
    when(metaData.getDatabaseMajorVersion()).thenReturn(0);
    when(metaData.getDatabaseMinorVersion()).thenReturn(0);
    return metaData;
  }

  @Test
  void consultsTheFallbackWhenDetectionFails() throws SQLException {
    var metaData = metaData("Greenplum Database", "6.25.3");

    var platform = Accent.builder().fallback(PostgreSQL::new).of(metaData);

    assertThat(platform).isInstanceOf(PostgreSQL.class);
    assertThat(platform.productName()).isEqualTo("Greenplum Database");
  }

  @Test
  void ignoresTheFallbackWhenDetectionSucceeds() throws SQLException {
    var metaData = metaData(ObservedStrings.MYSQL_NAME, ObservedStrings.MYSQL_VERSION);

    var platform = Accent.builder().fallback(PostgreSQL::new).of(metaData);

    assertThat(platform).isInstanceOf(MySQL.class);
  }

  @Test
  void stillReturnsUnknownIfTheFallbackDeclines() throws SQLException {
    var metaData = metaData("Greenplum Database", "6.25.3");

    var platform = Accent.builder().fallback(version -> null).of(metaData);

    assertThat(platform).isInstanceOf(Unknown.class);
  }

  @Test
  void returnsUnknownWithoutAFallback() throws SQLException {
    var metaData = metaData("Greenplum Database", "6.25.3");

    assertThat(Accent.builder().of(metaData)).isInstanceOf(Unknown.class);
  }
}
