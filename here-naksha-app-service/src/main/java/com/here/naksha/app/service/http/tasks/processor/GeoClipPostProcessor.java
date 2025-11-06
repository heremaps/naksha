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
package com.here.naksha.app.service.http.tasks.processor;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryFixer;

public final class GeoClipPostProcessor implements FeaturePostProcessor<XyzFeature> {

  private final Geometry clipGeometry;

  public GeoClipPostProcessor(Geometry clipGeometry) {
    this.clipGeometry = clipGeometry;
  }

  @Override
  public XyzFeature postProcess(XyzFeature feature) {
    // clip Feature geometry (if present) to a given clipGeo geometry
    final XyzGeometry xyzGeo = feature.getGeometry();
    if (xyzGeo != null) {
      // NOTE - in JTS when we say:
      //    GeometryFixer.fix(geom).intersection(bbox)
      // it is the best available way of clipping geometry, equivalent to PostGIS approach of:
      //    ST_Intersection(ST_MakeValid(geo, 'method=structure'), bbox)
      Geometry clippedGeo = GeometryFixer.fix(xyzGeo.getJTSGeometry()).intersection(clipGeometry);
      feature.setGeometry(XyzGeometry.convertJTSGeometry(clippedGeo));
    }
    return feature;
  }
}
