/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.core.models.geojson.implementation;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.models.geojson.coordinates.BBox;
import com.here.naksha.lib.core.models.geojson.coordinates.JTSHelper;
import com.here.naksha.lib.core.models.geojson.exceptions.InvalidGeometryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "GeometryCollection")
public class XyzGeometryCollection extends XyzGeometry {

  public static final String GEOMETRIES = "geometries";

  @JsonProperty(GEOMETRIES)
  private List<XyzGeometryItem> geometries = new ArrayList<>();

  @JsonGetter
  public List<XyzGeometryItem> getGeometries() {
    return this.geometries;
  }

  @SuppressWarnings("WeakerAccess")
  @JsonSetter
  public void setGeometries(List<XyzGeometryItem> geometries) {
    this.geometries = geometries;
  }

  public XyzGeometryCollection withGeometries(final List<XyzGeometryItem> geometries) {
    setGeometries(geometries);
    return this;
  }

  @Override
  public BBox calculateBBox() {
    if (this.geometries == null || this.geometries.size() == 0) {
      return null;
    }

    double minLon = Double.MAX_VALUE;
    double minLat = Double.MAX_VALUE;
    double maxLon = Double.MIN_VALUE;
    double maxLat = Double.MIN_VALUE;

    for (XyzGeometryItem geom : this.geometries) {
      BBox bbox = geom.calculateBBox();
      if (bbox != null) {
        if (bbox.minLon() < minLon) {
          minLon = bbox.minLon();
        }
        if (bbox.minLat() < minLat) {
          minLat = bbox.minLat();
        }
        if (bbox.maxLon() > maxLon) {
          maxLon = bbox.maxLon();
        }
        if (bbox.maxLat() > maxLat) {
          maxLat = bbox.maxLat();
        }
      }
    }

    if (minLon != Double.MAX_VALUE
        && minLat != Double.MAX_VALUE
        && maxLon != Double.MIN_VALUE
        && maxLat != Double.MIN_VALUE) {
      return new BBox(minLon, minLat, maxLon, maxLat);
    }
    return null;
  }

  @Override
  protected org.locationtech.jts.geom.GeometryCollection convertToJTSGeometry() {
    if (this.geometries == null || this.geometries.size() == 0) {
      return null;
    }

    org.locationtech.jts.geom.Geometry[] jtsGeometries =
        new org.locationtech.jts.geom.Geometry[this.geometries.size()];
    for (int i = 0; i < jtsGeometries.length; i++) {
      jtsGeometries[i] = this.geometries.get(i).getJTSGeometry();
    }

    return JTSHelper.factory.createGeometryCollection(jtsGeometries);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void validate() throws InvalidGeometryException {
    if (this.geometries == null || this.geometries.size() == 0) {
      return;
    }

    for (XyzGeometryItem geometry : this.geometries) {
      try {
        geometry.validate();
      } catch (InvalidGeometryException e) {
        throw new InvalidGeometryException("The geometry with type "
            + geometry.getClass().getSimpleName()
            + " is invalid, reason: "
            + e.getMessage());
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    XyzGeometryCollection that = (XyzGeometryCollection) o;
    return Objects.equals(geometries, that.geometries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(geometries);
  }
}
