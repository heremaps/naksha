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
package com.here.naksha.lib.core.models.geojson;

import static java.util.Objects.requireNonNull;
import static naksha.geo.NakshaGeoKt.sp_double;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import naksha.geo.BBox;
import org.junit.jupiter.api.Test;

// TODO: We should port these tests into `lib-geo` !!!
public class HQuadTest {

  private static final String base4QK = "12201203120220";
  private static final String base10QK = "377894440";
  private static final Double qk_WEST = requireNonNull(sp_double(13.359375));
  private static final Double qk_SOUTH = requireNonNull(sp_double(52.5146484375));
  private static final Double qk_EAST = requireNonNull(sp_double(13.38134765625));
  private static final Double qk_NORTH = requireNonNull(sp_double(52.53662109375));
  private static final BBox qk_bbox = new BBox(qk_WEST, qk_SOUTH, qk_EAST, qk_NORTH);

  @Test
  public void testBase4Quadkey() {
    HQuad hQuad = new HQuad(base4QK, true);
    BBox boundingBox = hQuad.getBoundingBox();
    assertEquals(qk_bbox.getWest(), boundingBox.getWest());
    assertEquals(qk_bbox.getSouth(), boundingBox.getSouth());
    assertEquals(qk_bbox.getEast(), boundingBox.getEast());
    assertEquals(qk_bbox.getNorth(), boundingBox.getNorth());
    assertEquals(14, hQuad.level);
    assertEquals(8800, hQuad.x);
    assertEquals(6486, hQuad.y);
    assertEquals(base4QK, hQuad.quadkey);
  }

  @Test
  public void testBase10Quadkey() {
    HQuad hQuad = new HQuad(base10QK, false);
    BBox boundingBox = hQuad.getBoundingBox();
    assertEquals(qk_bbox.getWest(), boundingBox.getWest());
    assertEquals(qk_bbox.getSouth(), boundingBox.getSouth());
    assertEquals(qk_bbox.getEast(), boundingBox.getEast());
    assertEquals(qk_bbox.getNorth(), boundingBox.getNorth());
    assertEquals(14, hQuad.level);
    assertEquals(8800, hQuad.x);
    assertEquals(6486, hQuad.y);
    assertEquals(base4QK, hQuad.quadkey);
  }

  @Test
  public void testLRC() {
    HQuad hQuad = new HQuad(8800, 6486, 14);
    BBox boundingBox = hQuad.getBoundingBox();
    assertEquals(qk_bbox.getWest(), boundingBox.getWest());
    assertEquals(qk_bbox.getSouth(), boundingBox.getSouth());
    assertEquals(qk_bbox.getEast(), boundingBox.getEast());
    assertEquals(qk_bbox.getNorth(), boundingBox.getNorth());
    assertEquals(14, hQuad.level);
    assertEquals(8800, hQuad.x);
    assertEquals(6486, hQuad.y);
    assertEquals(base4QK, hQuad.quadkey);
  }

  @Test
  public void testInvalidBase4QK() {
    assertThrows(IllegalArgumentException.class, () -> new HQuad("5031", true));
  }

  @Test
  public void testInvalidBase10QK() {
    assertThrows(IllegalArgumentException.class, () -> new HQuad("12s", false));
  }

  @Test
  public void testInvalidLRC() {
    assertThrows(IllegalArgumentException.class, () -> new HQuad(10, 10, 1));
  }

  private static final Double HTILE_WEST = requireNonNull(sp_double(13.623046875));
  private static final Double HTILE_SOUTH = requireNonNull(sp_double(52.20703125));
  private static final Double HTILE_EAST = requireNonNull(sp_double(13.7109375));
  private static final Double HTILE_NORTH = requireNonNull(sp_double(52.294921875));

  @Test
  public void testGeometryFromHereTileIdBase10QK() {
    final String tileId = "23618381";
    HQuad hQuad = new HQuad(tileId, false);
    BBox bbox = hQuad.getBoundingBox();
    assertEquals(HTILE_WEST, bbox.getWest(), "West coordinate doesn't match");
    assertEquals(HTILE_SOUTH, bbox.getSouth(), "South coordinate doesn't match");
    assertEquals(HTILE_EAST, bbox.getEast(), "East coordinate doesn't match");
    assertEquals(HTILE_NORTH, bbox.getNorth(), "North coordinate doesn't match");
  }

  @Test
  public void testGeometryFromHereTileIdBase4QK() {
    final String tileId = "122012031031";
    HQuad hQuad = new HQuad(tileId, true);
    BBox bbox = hQuad.getBoundingBox();
    assertEquals(bbox.getWest(), HTILE_WEST, "West coordinate doesn't match");
    assertEquals(bbox.getSouth(), HTILE_SOUTH, "South coordinate doesn't match");
    assertEquals(bbox.getEast(), HTILE_EAST, "East coordinate doesn't match");
    assertEquals(bbox.getNorth(), HTILE_NORTH, "North coordinate doesn't match");
  }
}
