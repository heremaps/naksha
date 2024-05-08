/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.core;

import static com.here.naksha.lib.core.NakshaVersion.v2_0_3;

import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Just an abstraction for Naksha versioning.
 *
 */
@SuppressWarnings("unused")
@AvailableSince(v2_0_3)
public class NakshaVersion implements Comparable<NakshaVersion> {
  /**
   * Naksha version constant. The last version compatible with XYZ-Hub.
   */
  public static final String v0_6 = "0.6.0";

  public static final String v2_0_0 = "2.0.0";
  public static final String v2_0_3 = "2.0.3";
  public static final String v2_0_4 = "2.0.4";
  public static final String v2_0_5 = "2.0.5";
  public static final String v2_0_6 = "2.0.6";
  public static final String v2_0_7 = "2.0.7";
  public static final String v2_0_8 = "2.0.8";
  public static final String v2_0_9 = "2.0.9";
  public static final String v2_0_10 = "2.0.10";
  public static final String v2_0_11 = "2.0.11";
  public static final String v2_0_12 = "2.0.12";
  public static final String v2_0_13 = "2.0.13";
  public static final String v2_0_14 = "2.0.14";
  public static final String v2_0_15 = "2.0.15";
  public static final String v3_0_0 = "3.0.0";
  public static final String v3_0_0_alpha_0 = "3.0.0-alpha.0";
  public static final String v3_0_0_alpha_1 = "3.0.0-alpha.1";
  public static final String v3_0_0_alpha_2 = "3.0.0-alpha.2";
  public static final String v3_0_0_alpha_8 = "3.0.0-alpha.8";
  public static final String v3_0_0_alpha_9 = "3.0.0-alpha.9";
  public static final String v3_0_0_alpha_10 = "3.0.0-alpha.10";
  public static final String v3_0_0_alpha_11 = "3.0.0-alpha.11";
  public static final String v3_0_0_alpha_12 = "3.0.0-alpha.12";
  public static final String v3_0_0_alpha_13 = "3.0.0-alpha.13";
  public static final String v3_0_0_alpha_14 = "3.0.0-alpha.14";
  public static final String v3_0_0_alpha_15 = "3.0.0-alpha.15";

  /**
   * The latest version of the naksha-extension stored in the resources.
   */
  @AvailableSince(v2_0_5)
  public static final NakshaVersion latest = of(v3_0_0_alpha_15);

  private final int major;
  private final int minor;
  private final int revision;

  @NotNull
  private final PreReleaseTag preReleaseTag;

  private final Integer preReleaseVersion;

  public enum PreReleaseTag {
    none(PRE_RELEASE_TAG_DEFAULT),
    alpha(0),
    beta(1);

    final int enc;

    PreReleaseTag(int enc) {
      this.enc = enc;
    }

    public static PreReleaseTag findByValue(int enc) {
      for (PreReleaseTag tag : values()) {
        if (tag.enc == enc) {
          return tag;
        }
      }
      throw new IllegalArgumentException("Unknown pre-release tag: " + enc);
    }
  }

  private static final int PRE_RELEASE_TAG_DEFAULT = 255;
  private static final int PRE_RELEASE_VERSION_DEFAULT = 255;

  /**
   * @param major             the major version (0-65535).
   * @param minor             the minor version (0-65535).
   * @param revision          the revision (0-65535).
   * @param preReleaseTag     the pre-release tag (alpha, beta, none).
   * @param preReleaseVersion the pre-release version (0-255 or null).
   */
  public NakshaVersion(
      int major, int minor, int revision, @NotNull PreReleaseTag preReleaseTag, Integer preReleaseVersion) {
    this.major = major;
    this.minor = minor;
    this.revision = revision;
    this.preReleaseTag = preReleaseTag;
    this.preReleaseVersion = preReleaseVersion;
  }

  /**
   * Parses the given version string and returns the Naksha version.
   *
   * @param version the version like "2.0.3".
   * @return the Naksha version.
   * @throws NumberFormatException if the given string is no valid version.
   */
  @AvailableSince(v2_0_3)
  public static @NotNull NakshaVersion of(@NotNull String version) throws NumberFormatException {
    final int majorEnd = version.indexOf('.');
    final int minorEnd = version.indexOf('.', majorEnd + 1);
    final int revisionEnd = version.indexOf('-', minorEnd + 1);
    final int preReleaseTagEnd = revisionEnd == -1 ? -1 : version.indexOf('.', revisionEnd + 1);
    final int major = Integer.parseInt(version.substring(0, majorEnd));
    final int minor = Integer.parseInt(version.substring(majorEnd + 1, minorEnd));
    final int revision = revisionEnd == -1
        ? Integer.parseInt(version.substring(minorEnd + 1))
        : Integer.parseInt(version.substring(minorEnd + 1, revisionEnd));
    final String preReleaseTagString =
        revisionEnd == -1 ? null : version.substring(revisionEnd + 1, preReleaseTagEnd);
    final PreReleaseTag preReleaseTag =
        preReleaseTagString == null ? PreReleaseTag.none : PreReleaseTag.valueOf(preReleaseTagString);
    final Integer preReleaseVersion =
        preReleaseTagEnd == -1 ? null : Integer.parseInt(version.substring(preReleaseTagEnd + 1));

    return new NakshaVersion(major, minor, revision, preReleaseTag, preReleaseVersion);
  }

  @AvailableSince(v2_0_3)
  public NakshaVersion(long value) {
    this(
        (int) ((value >>> 48) & 0xffff),
        (int) ((value >>> 32) & 0xffff),
        (int) ((value >>> 16) & 0xffff),
        PreReleaseTag.findByValue((int) ((value >>> 8) & 0xff)),
        (int) ((value >>> 8) & 0xff) == PRE_RELEASE_TAG_DEFAULT
                || ((value) & 0xff) == PRE_RELEASE_VERSION_DEFAULT
            ? null
            : (int) ((value) & 0xff));
  }

  @AvailableSince(v2_0_3)
  public int getMajor() {
    return major;
  }

  @AvailableSince(v2_0_3)
  public int getMinor() {
    return minor;
  }

  @AvailableSince(v2_0_3)
  public int getRevision() {
    return revision;
  }

  @AvailableSince(v3_0_0_alpha_0)
  public @NotNull PreReleaseTag getPreReleaseTag() {
    return preReleaseTag;
  }

  @AvailableSince(v3_0_0_alpha_0)
  public Integer getReleaseVersion() {
    return preReleaseVersion;
  }

  @AvailableSince(v2_0_3)
  public long toLong() {
    return ((major & 0xffffL) << 48)
        | ((minor & 0xffffL) << 32)
        | ((revision & 0xffffL) << 16)
        | (preReleaseTag.enc & 0xffL) << 8
        | (preReleaseVersion == null ? PRE_RELEASE_VERSION_DEFAULT & 0xffL : preReleaseVersion & 0xffL);
  }

  @Override
  public int compareTo(@NotNull NakshaVersion o) {
    final long result = toLong() - o.toLong();
    return result < 0 ? -1 : result == 0 ? 0 : 1;
  }

  @Override
  public int hashCode() {
    return (int) (toLong() >>> 16);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof NakshaVersion) {
      NakshaVersion o = (NakshaVersion) other;
      return this.toLong() == o.toLong();
    }
    return false;
  }

  @Override
  public @NotNull String toString() {
    return "" + major + '.' + minor + '.' + revision
        + (preReleaseTag == PreReleaseTag.none ? "" : "-" + preReleaseTag + '.' + preReleaseVersion);
  }
}
