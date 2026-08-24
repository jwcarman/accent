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

/** The database accent is talking to. */
public sealed interface Platform {

  /** The version information the driver reported for this platform. */
  Version version();

  /**
   * @return the raw product name, exactly as the driver reported it
   */
  default String productName() {
    return version().productName();
  }

  /**
   * @return the raw product version, exactly as the driver reported it
   */
  default String productVersion() {
    return version().productVersion();
  }

  /**
   * @return the database major version the driver reported
   */
  default int majorVersion() {
    return version().majorVersion();
  }

  /**
   * @return the database minor version the driver reported
   */
  default int minorVersion() {
    return version().minorVersion();
  }

  /**
   * Whether this platform supports the {@code FOR UPDATE SKIP LOCKED} clause with genuine
   * skip-locked semantics: a second transaction reading rows locked by a first must skip them
   * rather than block.
   *
   * <p>This is deliberately narrow. It does not cover SQL Server's {@code WITH (UPDLOCK,
   * READPAST)}, which is a different statement with different semantics, and it is not the first
   * member of a general capability bag.
   *
   * <p>A {@code false} answer is always safe: a caller falls back to plain {@code FOR UPDATE},
   * which blocks instead of skipping but is never incorrect. Every arm returning {@code true} is
   * backed by a contention test in accent's integration suite.
   *
   * @return true if concurrent claims skip locked rows
   */
  default boolean supportsSkipLocked() {
    return false;
  }

  /** PostgreSQL proper, having ruled out the engines that impersonate it. */
  record PostgreSQL(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 9;
    private static final int SKIP_LOCKED_MINOR = 5;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in PostgreSQL 9.5. This floor is measured, not merely
     * documented: contention testing against PostgreSQL 17 confirms it skips, and against 9.4.26
     * confirms it does not — {@code syntax error at or near "SKIP"}.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() > SKIP_LOCKED_MAJOR
          || (majorVersion() == SKIP_LOCKED_MAJOR && minorVersion() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * CockroachDB.
   *
   * <p>Through pgjdbc this engine is indistinguishable from PostgreSQL at the {@link
   * java.sql.DatabaseMetaData} level — it reports product name {@code PostgreSQL} and a bare
   * PostgreSQL version number. Its {@link #version()} therefore describes PostgreSQL, not
   * CockroachDB. accent identifies it by querying {@code SELECT version()}, and {@link #engine()}
   * carries CockroachDB's own version parsed from that same query — the only place the real version
   * appears.
   *
   * @param version what the driver reported, describing the PostgreSQL release CockroachDB
   *     emulates, not CockroachDB itself
   * @param engine CockroachDB's actual version, parsed from {@code SELECT version()}
   */
  record CockroachDB(Version version, EngineVersion engine) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 22;
    private static final int SKIP_LOCKED_MINOR = 2;

    /**
     * {@inheritDoc}
     *
     * <p>This is a discovered boundary, not a guess: contention testing across a version series
     * found v22.1.22 genuinely does not skip (it rejects {@code SKIP LOCKED} with {@code ERROR:
     * unimplemented: SKIP LOCKED lock wait policy is not supported}), while v22.2.19, v23.1.30, and
     * v24.1.32 all genuinely do. {@link #version()} cannot express this floor — CockroachDB reports
     * {@code productVersion} = {@code 13.0.0} at every one of those releases — so this gates on
     * {@link #engine()} instead, parsed from {@code SELECT version()}.
     *
     * <p>If {@link #engine()} could not be parsed, this returns {@code false}: detection still
     * succeeded (this is CockroachDB), but an unparseable version is no evidence of capability, and
     * {@code false} is always the safe answer.
     */
    @Override
    public boolean supportsSkipLocked() {
      return engine.major() > SKIP_LOCKED_MAJOR
          || (engine.major() == SKIP_LOCKED_MAJOR && engine.minor() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * YugabyteDB.
   *
   * <p>Reports product name {@code PostgreSQL}; its {@link #version()} describes the PostgreSQL
   * release it emulates, not YugabyteDB's own numbering. {@link #engine()} carries YugabyteDB's own
   * version, parsed from {@code SELECT version()}.
   *
   * @param version what the driver reported, describing the PostgreSQL release YugabyteDB emulates,
   *     not YugabyteDB itself
   * @param engine YugabyteDB's actual version, parsed from {@code SELECT version()}
   */
  record YugabyteDB(Version version, EngineVersion engine) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 2;
    private static final int SKIP_LOCKED_MINOR = 16;

    /**
     * {@inheritDoc}
     *
     * <p><strong>2.16 is the lowest version measured, not a discovered boundary.</strong> 2.16.9,
     * 2.18.9, 2.20.12, and 2024.1 all genuinely skip; 2.14.17 would not start on the test machine,
     * so nothing below 2.16.9 was ever measured. Unlike the CockroachDB floor, this is not a
     * confirmed line between "works" and "does not work" — it is simply the bottom of what was
     * observed. Versions in the {@code 2024.1}-style scheme parse as major 2024, minor 1, which is
     * correctly above this floor.
     *
     * <p>{@link #version()} cannot express any floor at all — YugabyteDB reports {@code
     * productVersion} such as {@code 11.2-YB-2.16.9.0-b0}, a bare PostgreSQL compatibility number —
     * so this gates on {@link #engine()} instead, parsed from {@code SELECT version()}.
     *
     * <p>If {@link #engine()} could not be parsed, this returns {@code false}: detection still
     * succeeded (this is YugabyteDB), but an unparseable version is no evidence of capability, and
     * {@code false} is always the safe answer.
     */
    @Override
    public boolean supportsSkipLocked() {
      return engine.major() > SKIP_LOCKED_MAJOR
          || (engine.major() == SKIP_LOCKED_MAJOR && engine.minor() >= SKIP_LOCKED_MINOR);
    }
  }

  /** MySQL proper, having ruled out MariaDB reached through a MySQL driver. */
  record MySQL(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 8;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in MySQL 8.0. This floor is measured, not merely documented:
     * contention testing against MySQL 8.4 confirms it skips, and against 5.7.44 confirms it does
     * not — a SQL syntax error near {@code 'SKIP LOCKED'}. The floor's minor version is 0, so a
     * major-version comparison alone is sufficient — there is no minor version below 0 that a
     * major-version match could wrongly exclude.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() >= SKIP_LOCKED_MAJOR;
    }
  }

  /**
   * MariaDB.
   *
   * <p>Reached through {@code mysql-connector-j} this server reports product name {@code MySQL};
   * only its product version names MariaDB.
   */
  record MariaDB(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 10;
    private static final int SKIP_LOCKED_MINOR = 6;

    /**
     * {@inheritDoc}
     *
     * <p>{@code SKIP LOCKED} arrived in MariaDB 10.6. This floor is measured, not merely
     * documented: contention testing against MariaDB 11.4 confirms it skips, and against 10.5.29
     * confirms it does not — a SQL syntax error near {@code 'SKIP'}.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() > SKIP_LOCKED_MAJOR
          || (majorVersion() == SKIP_LOCKED_MAJOR && minorVersion() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * Microsoft SQL Server.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false} here and must stay that way.
   * SQL Server has no {@code FOR UPDATE SKIP LOCKED} clause; its nearest equivalent is {@code WITH
   * (UPDLOCK, READPAST)}, a different statement with different semantics. {@code
   * supportsSkipLocked()} is documented as covering the {@code FOR UPDATE SKIP LOCKED} clause
   * specifically, and a contention test against SQL Server 2022 using that exact clause confirms
   * it: plain {@code FOR UPDATE} itself is rejected outside a cursor declaration ("FOR UPDATE
   * clause allowed only for DECLARE CURSOR"), so no skip is ever observed. Do not "fix" this arm to
   * {@code true} on the strength of {@code READPAST} — that is a different capability this
   * predicate does not cover.
   */
  record SqlServer(Version version) implements Platform {}

  /**
   * Oracle Database.
   *
   * <p>Its product version may span two lines, and does on 18c and later — but not on 11g XE, which
   * reports a single line ({@code Oracle Database 11g Express Edition Release 11.2.0.2.0 - 64bit
   * Production}). A heuristic anchored with {@code $} or one that requires a newline breaks on one
   * of those two shapes or the other. accent is unaffected because it uses the integer accessors
   * ({@link #majorVersion()}/{@link #minorVersion()}), never string-parses {@link
   * #productVersion()} — which is the point.
   */
  record Oracle(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 11;

    /**
     * {@inheritDoc}
     *
     * <p><strong>11 is the lowest version measured, not a discovered boundary.</strong> 11.2.0.2,
     * 18.4.0, 21.3.0, and 23.26 all genuinely skip by contention test; no Oracle 10g image is
     * published, so nothing below 11.2.0.2 could ever be tested. That {@code SKIP LOCKED} arrived
     * in Oracle 11 is Oracle's own documentation; what accent verified is that 11.2.0.2 skips.
     *
     * <p>Oracle 18c reports {@link #minorVersion()} ({@code getDatabaseMinorVersion()}) as {@code
     * 0} despite being release 18.4 — harmless today because this floor is major-only, but it would
     * silently break anyone who later adds a minor comparison here.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() >= SKIP_LOCKED_MAJOR;
    }
  }

  /**
   * IBM Db2. Its product name carries a platform suffix, such as {@code DB2/LINUXX8664}.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. Db2 12.1 parses {@code FOR
   * UPDATE SKIP LOCKED} without error, but a contention test shows it does not block — it ignores
   * the clause and returns both rows, including the one the first connection still holds locked in
   * an open transaction:
   *
   * <pre>{@code did not skip: returned [1, 2] — clause accepted and ignored}</pre>
   *
   * <p>This is a worse trap than blocking would be: a caller doing outbox claiming against Db2 on
   * the strength of the syntax being accepted would hand out the same row to two workers at once,
   * silently, under concurrency — exactly the failure mode this predicate exists to prevent. Do not
   * "fix" this arm to {@code true} on the strength of the syntax being accepted; that syntax being
   * accepted is precisely what makes this arm dangerous to get wrong.
   */
  record Db2(Version version) implements Platform {}

  /** H2. */
  record H2(Version version) implements Platform {

    private static final int SKIP_LOCKED_MAJOR = 2;
    private static final int SKIP_LOCKED_MINOR = 2;

    /**
     * {@inheritDoc}
     *
     * <p>This is a discovered boundary, not a guess: contention testing across a classpath matrix
     * (H2 is not containerisable, so each version's jar was run in turn against the same contention
     * harness) found 1.4.200, 2.0.206, and 2.1.214 all genuinely reject the clause with a syntax
     * error — {@code Syntax error in SQL statement "... FOR UPDATE SKIP[*] LOCKED"} — while 2.2.224
     * and 2.3.232 both genuinely skip. H2 reports its own version honestly, so unlike CockroachDB
     * or YugabyteDB this needs no separate {@code engine()} component; the floor gates directly on
     * {@link #majorVersion()}/{@link #minorVersion()}.
     */
    @Override
    public boolean supportsSkipLocked() {
      return majorVersion() > SKIP_LOCKED_MAJOR
          || (majorVersion() == SKIP_LOCKED_MAJOR && minorVersion() >= SKIP_LOCKED_MINOR);
    }
  }

  /**
   * HSQLDB, which reports itself as {@code HSQL Database Engine}.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. HSQLDB rejects the {@code
   * SKIP LOCKED} clause outright ({@code unexpected token: SKIP}), confirmed by contention test
   * against HSQLDB 2.7.
   */
  record HSQLDB(Version version) implements Platform {}

  /**
   * SQLite.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. SQLite has no row-level
   * locking model at all — even plain {@code FOR UPDATE} is a syntax error — confirmed by
   * contention test against SQLite 3.47.
   */
  record SQLite(Version version) implements Platform {}

  /**
   * Apache Derby.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. Derby rejects the {@code
   * SKIP LOCKED} clause outright, confirmed by contention test against Derby 10.17.
   */
  record Derby(Version version) implements Platform {}

  /**
   * A database accent has not been taught.
   *
   * <p>This arm exists so an exhaustive {@code switch} needs no {@code default}, which forces every
   * caller to decide at compile time what happens for an unrecognised database. It carries the raw
   * strings so the decision can be logged actionably and the gap reported.
   *
   * <p>{@code Unknown} means "asked, did not recognise". It never means "could not ask" — a failure
   * to reach the database raises {@link AccentException} instead.
   *
   * <p>{@link #supportsSkipLocked()} correctly inherits {@code false}. accent does not know what
   * database this is, so it cannot claim any capability for it — {@code false} is the only safe
   * answer.
   */
  record Unknown(Version version) implements Platform {}

  /**
   * What the JDBC driver reported, verbatim.
   *
   * <p>These values are raw. For a database that impersonates another, they describe the
   * <em>impersonation</em>, not the engine: pgjdbc reports a PostgreSQL version number for
   * CockroachDB, so a {@link CockroachDB} carries a PostgreSQL version. Never parse an engine
   * version out of these fields expecting the engine's own numbering.
   *
   * <p>{@code majorVersion} and {@code minorVersion} come from {@link
   * java.sql.DatabaseMetaData#getDatabaseMajorVersion()} and {@link
   * java.sql.DatabaseMetaData#getDatabaseMinorVersion()}. They are the only trustworthy numeric
   * source: Db2 reports a build identifier such as {@code SQL120100} as its product version, and
   * Oracle's product version spans two lines and names a marketing release that disagrees with its
   * release number.
   *
   * @param productName the driver's database product name
   * @param productVersion the driver's database product version, which may contain newlines
   * @param majorVersion the driver's database major version
   * @param minorVersion the driver's database minor version
   */
  record Version(String productName, String productVersion, int majorVersion, int minorVersion) {

    /** Validates that the reported strings are present. */
    public Version {
      Objects.requireNonNull(productName, "productName");
      Objects.requireNonNull(productVersion, "productVersion");
    }
  }

  /**
   * An impostor engine's own version, parsed out of {@code SELECT version()} rather than out of
   * {@link Version}.
   *
   * <p>{@link CockroachDB} and {@link YugabyteDB} report a {@link Version} describing the
   * PostgreSQL release they emulate, not their own release — that is why this exists as a separate
   * component rather than being folded into {@link Version}. {@code raw} holds the full {@code
   * SELECT version()} string, which is independently useful: it is the only way a caller can learn
   * the real CockroachDB or YugabyteDB version at all.
   *
   * <p>When the version could not be parsed out of {@code raw}, {@code major} and {@code minor} are
   * both {@code 0} — never a guess, and never treated as evidence of any capability. A {@code
   * supportsSkipLocked()} floor gated on an unparseable {@code EngineVersion} always answers {@code
   * false}.
   *
   * @param raw the full, unparsed {@code SELECT version()} result
   * @param major the engine's own major version, or 0 if it could not be parsed from {@code raw}
   * @param minor the engine's own minor version, or 0 if it could not be parsed from {@code raw}
   */
  record EngineVersion(String raw, int major, int minor) {

    /** Validates that the raw string is present. */
    public EngineVersion {
      Objects.requireNonNull(raw, "raw");
    }
  }
}
