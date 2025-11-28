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

import naksha.geo.ProxyGeoUtil;
import naksha.geo.SpGeometry;
import naksha.model.objects.NakshaFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryFixer;

public final class GeoClipPostProcessor implements FeaturePostProcessor<NakshaFeature> {

  private final SpGeometry clipGeometry;

  public GeoClipPostProcessor(SpGeometry clipGeometry) {
    this.clipGeometry = clipGeometry;
  }

  @Override
  public NakshaFeature postProcess(NakshaFeature feature) {
    // clip Feature geometry (if present) to a given clipGeo geometry
    final SpGeometry geo = feature.getGeometry();
    if (geo != null) {
      Geometry jtsGeo = ProxyGeoUtil.toJtsGeometry(geo);
      Geometry jtsClip = ProxyGeoUtil.toJtsGeometry(clipGeometry);
      Geometry clippedGeo = GeometryFixer.fix(jtsGeo).intersection(jtsClip);
      feature.setGeometry(ProxyGeoUtil.toProxyGeometry(clippedGeo));
    }
    return feature;
  }
}
