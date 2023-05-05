/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.xyz.models.geojson.implementation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.here.xyz.models.geojson.coordinates.BBox;
import com.here.xyz.models.geojson.declaration.IBoundedCoordinates;

@JsonSubTypes({
    @JsonSubTypes.Type(value = Point.class, name = "Point"),
    @JsonSubTypes.Type(value = MultiPoint.class, name = "MultiPoint"),
    @JsonSubTypes.Type(value = LineString.class, name = "LineString"),
    @JsonSubTypes.Type(value = MultiLineString.class, name = "MultiLineString"),
    @JsonSubTypes.Type(value = Polygon.class, name = "Polygon"),
    @JsonSubTypes.Type(value = MultiPolygon.class, name = "MultiPolygon")
})
public abstract class GeometryItem extends Geometry {

  public abstract IBoundedCoordinates getCoordinates();

  @Override
  public BBox calculateBBox() {
    final IBoundedCoordinates coordinates = getCoordinates();
    if (coordinates != null) {
      return coordinates.calculateBBox();
    }

    return null;
  }
}
