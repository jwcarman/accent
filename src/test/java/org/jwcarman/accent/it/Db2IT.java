package org.jwcarman.accent.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.jwcarman.accent.Accent;
import org.jwcarman.accent.Platform;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Db2. Tagged {@code heavy}: the image is licence-gated, slow to start, and publishes no
 * {@code linux/arm64} manifest, so on Apple Silicon it runs only under emulation. Treat this as
 * CI-verified rather than reliably reproducible on a workstation.
 *
 * <p>Db2's product name carries a host-architecture suffix — {@code DB2/LINUXX8664} here,
 * {@code DB2/NT64} elsewhere — so only a prefix match is safe. Its product version is
 * {@code SQL120100}, a build identifier from which nothing parses.
 */
@Tag("heavy")
@Testcontainers
class Db2IT {

  @Container
  private static final Db2Container DB2 =
      new Db2Container(DockerImageName.parse("icr.io/db2_community/db2:12.1.0.0")).acceptLicense();

  @Test
  void isDetectedByPrefixDespiteAnArchitectureSpecificName() throws SQLException {
    try (var connection =
        Drivers.connect(
            new com.ibm.db2.jcc.DB2Driver(),
            DB2.getJdbcUrl(),
            DB2.getUsername(),
            DB2.getPassword())) {

      var platform = Accent.of(connection);

      assertThat(platform).isInstanceOf(Platform.Db2.class);
      assertThat(platform.productName()).startsWith("DB2/");
      // Not a dotted version. Anything splitting on '.' produces garbage.
      assertThat(platform.productVersion()).startsWith("SQL");
      assertThat(platform.majorVersion()).isEqualTo(12);
      assertThat(platform.minorVersion()).isEqualTo(1);
    }
  }
}
