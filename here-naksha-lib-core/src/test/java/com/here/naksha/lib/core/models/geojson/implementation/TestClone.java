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
package com.here.naksha.lib.core.models.geojson.implementation;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.lib.core.models.geojson.coordinates.PointCoordinates;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class TestClone {

  public static XyzFeatureCollection generateRandomFeatures(int featureCount, int propertyCount)
      throws JsonProcessingException {
    XyzFeatureCollection collection = new XyzFeatureCollection();
    Random random = new Random();

    List<String> pKeys = Stream.generate(() -> RandomStringUtils.randomAlphanumeric(10))
        .limit(propertyCount)
        .toList();
    final ArrayList<XyzFeature> features = new ArrayList<>();
    for (int i = 0; i < featureCount; i++) {
      final XyzFeature f = new XyzFeature(RandomStringUtils.randomAlphanumeric(8));
      f.setGeometry(new XyzPoint()
          .withCoordinates(
              new PointCoordinates(360d * random.nextDouble() - 180d, 180d * random.nextDouble() - 90d)));
      pKeys.forEach(p -> f.getProperties().put(p, RandomStringUtils.randomAlphanumeric(8)));
      features.add(f);
    }
    collection.setFeatures(features);
    return collection;
  }

  @Test
  public void testAsMap() throws JsonProcessingException {
    XyzFeatureCollection collection = generateRandomFeatures(1, 1);
    XyzFeature feature = collection.getFeatures().get(0);
    feature.asMap();
  }

  @Test
  public void testClone() throws JsonProcessingException {
    XyzFeatureCollection collection = generateRandomFeatures(1, 1);
    XyzFeature feature = collection.getFeatures().get(0);
    feature.getProperties().setXyzNamespace(new XyzNamespace().setTags(Arrays.asList("a", "b", "c"), false));
    XyzFeature copy = feature.deepClone();
    copy.setId("changed");
    assertNotEquals(copy.getId(), feature.getId());
  }
}
