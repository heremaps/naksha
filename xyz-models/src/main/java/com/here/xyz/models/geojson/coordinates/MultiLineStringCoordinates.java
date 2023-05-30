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

package com.here.xyz.models.geojson.coordinates;

import com.here.xyz.models.geojson.declaration.IBoundedCoordinates;
import java.util.ArrayList;

public class MultiLineStringCoordinates extends ArrayList<LineStringCoordinates> implements IBoundedCoordinates {

  public MultiLineStringCoordinates() {
    super();
  }

  public MultiLineStringCoordinates(int size) {
    super(size);
  }

  public BBox calculateBBox() {
    return IBoundedCoordinates.calculate(this);
  }
}
