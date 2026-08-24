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
