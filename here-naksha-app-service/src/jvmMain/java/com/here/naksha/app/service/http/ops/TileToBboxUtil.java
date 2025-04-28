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
package com.here.naksha.app.service.http.ops;

import static com.here.naksha.common.http.apis.ApiParamsConst.TILE_TYPE_QUADKEY;

import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import naksha.geo.SpBoundingBox;
import naksha.geo.SpPolygon;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import org.jetbrains.annotations.NotNull;

public class TileToBboxUtil {

  private static final boolean DONT_CLONE = false;

  private TileToBboxUtil() {
  }

  public static @NotNull SpPolygon bboxPolygonForTile(
      final @NotNull String tileType,
      final @NotNull String tileId,
      final int margin
  ) {
    try {
      if (!TILE_TYPE_QUADKEY.equals(tileType)) {
        throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Tile type " + tileType + " not supported");
      }
      return bboxForTile(tileId).addMargin(margin).toPolygon();
    } catch (Exception ex) {
      throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Error interpreting tile input: " + ex.getMessage());
    }
  }

  private static SpBoundingBox bboxForTile(String tileId) {
    return WebMercatorTile.forQuadkey(tileId).getBBox(DONT_CLONE);
  }
}
