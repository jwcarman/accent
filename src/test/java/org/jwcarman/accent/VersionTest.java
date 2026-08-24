package org.jwcarman.accent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.jwcarman.accent.Platform.Version;
import org.junit.jupiter.api.Test;

class VersionTest {

  @Test
  void exposesExactlyWhatTheDriverReported() {
    var version = new Version("PostgreSQL", "17.10 (Debian 17.10-1.pgdg13+1)", 17, 10);

    assertThat(version.productName()).isEqualTo("PostgreSQL");
    assertThat(version.productVersion()).isEqualTo("17.10 (Debian 17.10-1.pgdg13+1)");
    assertThat(version.majorVersion()).isEqualTo(17);
    assertThat(version.minorVersion()).isEqualTo(10);
  }

  @Test
  void preservesAMultiLineProductVersion() {
    // Oracle really does report two lines; see docs/observed-strings.md.
    var productVersion =
        """
        Oracle AI Database 26ai Free Release 23.26.2.0.0 - Develop, Learn, and Run for Free
        Version 23.26.2.0.0""";

    var version = new Version("Oracle", productVersion, 23, 26);

    assertThat(version.productVersion()).isEqualTo(productVersion).contains("\n");
  }

  @Test
  void rejectsANullProductName() {
    assertThatNullPointerException().isThrownBy(() -> new Version(null, "1.0", 1, 0));
  }

  @Test
  void rejectsANullProductVersion() {
    assertThatNullPointerException().isThrownBy(() -> new Version("H2", null, 2, 3));
  }
}
