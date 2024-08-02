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
package com.here.naksha.lib.core.models.geojson.coordinates.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import naksha.base.FromJsonOptions;
import naksha.base.JvmMap;
import naksha.base.Platform;
import naksha.geo.SpBoundingBox;
import naksha.geo.SpMultiPolygon;
import naksha.geo.PointCoord;
import naksha.geo.SpPoint;
import org.junit.jupiter.api.Test;

public class BBoxTest {

  @Test
  public void pointCoordinates() throws Exception {
    String pointGJ = "{\"type\":\"Point\",\"coordinates\":[1,1]}";
    JvmMap jvmMap = (JvmMap) Platform.fromJSON(pointGJ, FromJsonOptions.getDEFAULT());
    SpPoint point = jvmMap.proxy(Platform.klassOf(SpPoint.class));
    PointCoord coordinates = point.getCoordinates();
//    final BoundingBoxProxy bbox = new BoundingBoxProxy(coordinates);
//
//    assertEquals(1d, bbox.getMinLongitude(), 0.0);
//    assertEquals(1d, bbox.getMaxLongitude(), 0.0);
//    assertEquals(1d, bbox.getMinLatitude(), 0.0);
//    assertEquals(1d, bbox.getMaxLatitude(), 0.0);
  }

  @Test
  public void multipolygonCoordinates() throws Exception {
    String multipolygonGJ =
        "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[101.2,1.2],[101.8,1.2],[101.8,1.8],[101.2,1.8],[101.2,1.2]],[[101.2,1.2],[101.3,1.2],[101.3,1.3],[101.2,1.3],[101.2,1.2]],[[101.6,1.4],[101.7,1.4],[101.7,1.5],[101.6,1.5],[101.6,1.4]],[[101.5,1.6],[101.6,1.6],[101.6,1.7],[101.5,1.7],[101.5,1.6]]],[[[100.0,0.0],[101.0,0.0],[101.0,1.0],[100.0,1.0],[100.0,0.0]],[[100.35,0.35],[100.65,0.35],[100.65,0.65],[100.35,0.65],[100.35,0.35]]]]}";
    JvmMap jvmMap = (JvmMap) Platform.fromJSON(multipolygonGJ, FromJsonOptions.getDEFAULT());
    SpMultiPolygon multipolygon = jvmMap.proxy(Platform.klassOf(SpMultiPolygon.class));
    SpBoundingBox bbox = new SpBoundingBox(multipolygon.getCoordinates());

    assertEquals(100.0, bbox.getMinLongitude(), 0.0);
    assertEquals(101.8, bbox.getMaxLongitude(), 0.0);
    assertEquals(0.0, bbox.getMinLatitude(), 0.0);
    assertEquals(1.8, bbox.getMaxLatitude(), 0.0);
  }
}
