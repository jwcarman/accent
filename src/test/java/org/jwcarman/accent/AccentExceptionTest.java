package org.jwcarman.accent;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class AccentExceptionTest {

  @Test
  void carriesItsMessageAndCause() {
    var cause = new SQLException("connection refused");

    var exception = new AccentException("could not read database metadata", cause);

    assertThat(exception).hasMessage("could not read database metadata").hasCause(cause);
  }

  @Test
  void isUnchecked() {
    assertThat(RuntimeException.class).isAssignableFrom(AccentException.class);
  }
}
