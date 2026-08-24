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

import java.io.Serial;

/**
 * Signals that accent could not ask the database what it is.
 *
 * <p>This is distinct from {@link Platform.Unknown}. "Could not ask" and "asked, did not recognise"
 * are different facts and accent never collapses them: a connection failure raises this exception
 * rather than returning {@code Unknown}.
 */
public final class AccentException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a new exception.
   *
   * @param message what accent was attempting
   * @param cause the underlying failure, typically a {@link java.sql.SQLException}
   */
  public AccentException(String message, Throwable cause) {
    super(message, cause);
  }
}
