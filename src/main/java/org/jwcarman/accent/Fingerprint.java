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

import java.util.Objects;
import org.jwcarman.accent.Platform.Version;

/**
 * Everything accent read from a database, and the input to detection.
 *
 * <p>This is deliberately not {@link Version}: detection needs one reading that callers never see.
 * {@code versionQuery} holds the result of {@code SELECT version()} and is populated only for
 * families that cannot be identified from metadata alone. It is {@code null} for every other
 * family, which is the normal case and not an error.
 *
 * @param productName from {@link java.sql.DatabaseMetaData#getDatabaseProductName()}
 * @param productVersion from {@link java.sql.DatabaseMetaData#getDatabaseProductVersion()}
 * @param majorVersion from {@link java.sql.DatabaseMetaData#getDatabaseMajorVersion()}
 * @param minorVersion from {@link java.sql.DatabaseMetaData#getDatabaseMinorVersion()}
 * @param versionQuery the result of {@code SELECT version()}, or null if the family did not need it
 */
record Fingerprint(
    String productName,
    String productVersion,
    int majorVersion,
    int minorVersion,
    String versionQuery) {

  Fingerprint {
    Objects.requireNonNull(productName, "productName");
    Objects.requireNonNull(productVersion, "productVersion");
  }

  /**
   * @return the public value type carried on every {@link Platform} arm
   */
  Version toVersion() {
    return new Version(productName, productVersion, majorVersion, minorVersion);
  }
}
