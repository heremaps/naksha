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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class NakshaVersionTest {

  @Test
  void testBasics() {
    NakshaVersion v = NakshaVersion.of(NakshaVersion.v2_0_3);
    assertNotNull(v);
    assertEquals(2, v.getMajor());
    assertEquals(0, v.getMinor());
    assertEquals(3, v.getRevision());

    v = new NakshaVersion(1, 2, 3, null, null);
    assertEquals(1, v.getMajor());
    assertEquals(2, v.getMinor());
    assertEquals(3, v.getRevision());
    assertEquals("1.2.3", v.toString());
    assertEquals(4295098371L, v.toLong());

    final NakshaVersion fromLong = new NakshaVersion(4295098371L);
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
    assertEquals(281487861612544L, v.toLong());

    final NakshaVersion fromLong = new NakshaVersion(281487861612544L);
    assertEquals(v.getMajor(), fromLong.getMajor());
    assertEquals(v.getMinor(), fromLong.getMinor());
    assertEquals(v.getRevision(), fromLong.getRevision());
    assertEquals(v.getPreReleaseTag(), fromLong.getPreReleaseTag());
    assertEquals(v.getReleaseVersion(), fromLong.getReleaseVersion());
  }
}
