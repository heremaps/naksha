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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NakshaVersionTest {

  @Test
  void testBasics() {
    NakshaVersion v = NakshaVersion.of(NakshaVersion.v2_0_3);
    assertNotNull(v);
    assertEquals(2, v.getMajor());
    assertEquals(0, v.getMinor());
    assertEquals(3, v.getRevision());
    assertEquals(NakshaVersion.PreReleaseTag.none, v.getPreReleaseTag());
    assertNull(v.getReleaseVersion());

    v = new NakshaVersion(1, 2, 3, NakshaVersion.PreReleaseTag.none, null);
    assertEquals(1, v.getMajor());
    assertEquals(2, v.getMinor());
    assertEquals(3, v.getRevision());
    assertEquals(NakshaVersion.PreReleaseTag.none, v.getPreReleaseTag());
    assertNull(v.getReleaseVersion());
    assertEquals("1.2.3", v.toString());

    final NakshaVersion fromLong = new NakshaVersion(v.toLong());
    assertEquals(v.getMajor(), fromLong.getMajor());
    assertEquals(v.getMinor(), fromLong.getMinor());
    assertEquals(v.getRevision(), fromLong.getRevision());
    assertEquals(v.getPreReleaseTag(), fromLong.getPreReleaseTag());
    assertEquals(v.getReleaseVersion(), fromLong.getReleaseVersion());
  }

  @Test
  void testPreRelease() {
    NakshaVersion v = NakshaVersion.of(NakshaVersion.v3_0_0_alpha_0);
    assertNotNull(v);
    assertEquals(3, v.getMajor());
    assertEquals(0, v.getMinor());
    assertEquals(0, v.getRevision());
    assertEquals(NakshaVersion.PreReleaseTag.alpha, v.getPreReleaseTag());
    assertEquals((byte) 0, v.getReleaseVersion());
    assertEquals(NakshaVersion.v3_0_0_alpha_0, v.toString());

    final NakshaVersion fromLong = new NakshaVersion(v.toLong());
    assertEquals(v.getMajor(), fromLong.getMajor());
    assertEquals(v.getMinor(), fromLong.getMinor());
    assertEquals(v.getRevision(), fromLong.getRevision());
    assertEquals(v.getPreReleaseTag(), fromLong.getPreReleaseTag());
    assertEquals(v.getReleaseVersion(), fromLong.getReleaseVersion());
  }

  @Test
  void testOrder() {
    final NakshaVersion v1 = NakshaVersion.of(NakshaVersion.v2_0_3);
    final NakshaVersion v2 = NakshaVersion.of(NakshaVersion.v2_0_4);
    final NakshaVersion v3 = NakshaVersion.of(NakshaVersion.v2_0_5);
    final NakshaVersion v4 = NakshaVersion.of(NakshaVersion.v3_0_0_alpha_0);
    final NakshaVersion v5 = NakshaVersion.of("3.0.0-alpha.1");
    final NakshaVersion v6 = NakshaVersion.of("3.0.0-beta.0");
    final NakshaVersion v7 = NakshaVersion.of("3.0.0");

    assertEquals(-1, v1.compareTo(v2));
    assertEquals(-1, v2.compareTo(v3));
    assertEquals(-1, v3.compareTo(v4));
    assertEquals(-1, v4.compareTo(v5));
    assertEquals(-1, v5.compareTo(v6));
    assertEquals(-1, v6.compareTo(v7));
    assertEquals(1, v2.compareTo(v1));
    assertEquals(1, v3.compareTo(v2));
    assertEquals(1, v4.compareTo(v3));
    assertEquals(1, v5.compareTo(v4));
    assertEquals(1, v6.compareTo(v5));
    assertEquals(1, v7.compareTo(v6));
    assertEquals(0, v1.compareTo(v1));
    assertEquals(0, v2.compareTo(v2));
    assertEquals(0, v3.compareTo(v3));
    assertEquals(0, v4.compareTo(v4));
    assertEquals(0, v5.compareTo(v5));
    assertEquals(0, v6.compareTo(v6));
    assertEquals(0, v7.compareTo(v7));
  }
}
