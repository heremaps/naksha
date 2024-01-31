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
package com.here.naksha.lib.core.models.storage.transformation;

import org.jetbrains.annotations.Nullable;

public class BufferTransformation extends GeometryTransformation {

  private final double distance;
  private final @Nullable String properties;

  public BufferTransformation(double distance, @Nullable String properties) {
    this(distance, properties, null);
  }

  public BufferTransformation(
      double distance, @Nullable String properties, @Nullable GeometryTransformation childTransformation) {
    super(childTransformation);
    this.distance = distance;
    this.properties = properties;
  }

  public double getDistance() {
    return distance;
  }

  public String getProperties() {
    if (properties == null) {
      return "";
    }
    return properties;
  }

  public static GeometryTransformation bufferInRadius(double distance) {
    return new BufferTransformation(distance, null);
  }

  public static GeometryTransformation bufferInMeters(double distance) {
    return new BufferTransformation(distance, null, new GeographyTransformation());
  }
}
