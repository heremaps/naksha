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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.LazyParsableFeatureList;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LazyParsedFeatureCollectionTest {

  @Test
  public void testDeserializeWithPartialView() throws Exception {
    final InputStream is = LazyParsedFeatureCollectionTest.class.getResourceAsStream(
        "/com/here/xyz/test/feature_collection_example.json");

    final XyzFeatureCollection response = JsonSerializable.deserialize(is);

    assertNotNull(response);
    Field features = response.getClass().getDeclaredField("features");
    features.setAccessible(true);
    //noinspection unchecked
    LazyParsableFeatureList lp = (LazyParsableFeatureList) features.get(response);
    Field value = lp.getClass().getDeclaredField("value");
    value.setAccessible(true);
    assertNull(value.get(lp));

    response.serialize();

    features = response.getClass().getDeclaredField("features");
    features.setAccessible(true);
    //noinspection unchecked
    lp = (LazyParsableFeatureList) features.get(response);
    value = lp.getClass().getDeclaredField("value");
    value.setAccessible(true);
    assertNull(value.get(lp));

    assertNotNull(response.getFeatures());
    assertEquals(1, response.getFeatures().size());

    XyzFeature feature = response.getFeatures().get(0);
    assertEquals("Q45671", feature.getId());
  }

  @Test
  public void testDeserializeWithFullView() throws Exception {
    try (final InputStream is = LazyParsedFeatureCollectionTest.class.getResourceAsStream(
        "/com/here/xyz/test/feature_collection_example.json")) {
      final XyzFeatureCollection response = JsonSerializable.deserialize(is);
      assertNotNull(response);
      assertNotNull(response.getFeatures());
      assertEquals(1, response.getFeatures().size());

      XyzFeature feature = response.getFeatures().get(0);
      assertNotNull(feature);
      assertNotNull(feature.get("customString"));
      assertNotNull(feature.getProperties());
      assertNotNull(feature.getProperties().getXyzNamespace());
      assertNull(feature.getBbox());

      assertEquals("Q45671", feature.getId());
    }
  }

  @Test
  public void testSerialize() throws Exception {
    try (final InputStream is =
        LazyParsedFeatureCollectionTest.class.getResourceAsStream("/com/here/xyz/test/one_feature.json")) {
      final XyzFeature f = JsonSerializable.deserialize(is);
      assertNotNull(f);
      assertEquals("Q45671", f.getId());
      assertEquals("string_value", f.get("customString"));
      assertEquals(4, (int) f.get("customLong"));
      assertNotNull(f.getGeometry());
      assertTrue(f.getGeometry() instanceof XyzPoint);
      assertEquals(
          -2.960827778D, ((XyzPoint) f.getGeometry()).getCoordinates().getLongitude(), 0.000000002);
      assertEquals(
          53.430819444, ((XyzPoint) f.getGeometry()).getCoordinates().getLatitude(), 0);
      assertEquals(0D, ((XyzPoint) f.getGeometry()).getCoordinates().getAltitude(), 0);
      assertNotNull(f.getProperties());
      assertEquals("Anfield", f.getProperties().get("name"));
      assertEquals("association football", f.getProperties().get("sport"));
      assertEquals(54167, (int) f.getProperties().get("capacity"));
      assertNotNull(f.getProperties().get("@ns:com:here:maphub"));
      assertTrue(f.getProperties().get("@ns:com:here:maphub") instanceof Map);

      Map<String, Object> maphub = (Map<String, Object>) f.getProperties().get("@ns:com:here:maphub");
      assertNotNull(maphub);
      assertEquals("8100c1baecf3749485ccba46529e751d683d1a4f", maphub.get("previousGuid"));
      assertEquals("15be2f6763b783457d71f2b38a45b8c6bf28c9dc", maphub.get("guid"));
      assertEquals("grp|xyzhub|data", maphub.get("layerId"));
      assertEquals(-372, maphub.get("id"));
      assertEquals(1487963959666L, maphub.get("createdTS"));
      assertEquals(1488896925130L, maphub.get("lastUpdateTS"));

      // assertEquals("my-space", f.getProperties().getXyzNamespace().getSpace());
      assertNotNull(f.getProperties().getXyzNamespace().getTags());
      assertArrayEquals(
          new String[] {"stadium", "soccer"},
          f.getProperties().getXyzNamespace().getTags().toArray());

      assertNull(f.getBbox());
      f.calculateAndSetBbox(true);
      assertNotNull(f.getBbox());
    }
  }

  @Test
  public void testDeserializeWithSeveralObjects() {
    try (InputStream is =
        LazyParsedFeatureCollectionTest.class.getResourceAsStream("/com/here/xyz/test/one_feature.json")) {
      final String jsonFeature = inputStreamToString(is);

      final int max = 10;
      List<XyzFeature> features = new ArrayList<>();
      for (int i = 0; i < max; i++) {
        assert jsonFeature != null;
        XyzFeature f = new ObjectMapper().readValue(jsonFeature, XyzFeature.class);
        f.setId("my_" + i);
        f.getProperties().put("from_loop", i);
        features.add(f);
      }

      XyzFeatureCollection fc = new XyzFeatureCollection();
      fc.setLazyParsableFeatureList(features);
      fc.calculateAndSetBBox(true);

      String severalFeaturesIntoFeatureCollection = new ObjectMapper().writeValueAsString(fc);

      //noinspection UnusedAssignment
      XyzFeatureCollection response =
          new ObjectMapper().readValue(severalFeaturesIntoFeatureCollection, XyzFeatureCollection.class);

      response = JsonSerializable.deserialize(severalFeaturesIntoFeatureCollection);

      //noinspection unused
      String responseSerialized = response.serialize();
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  /** Pretty naive solution, just to be used in these tests */
  private String inputStreamToString(InputStream is) {
    try (BufferedInputStream bis = new BufferedInputStream(is)) {
      int size = is.available();
      byte[] byteArray = new byte[size];
      //noinspection ResultOfMethodCallIgnored
      bis.read(byteArray);

      return new String(byteArray);
    } catch (IOException e) {
      return null;
    }
  }

  @Test
  public void testSerializeWithoutOrder() throws IOException {
    try (final InputStream is = LazyParsedFeatureCollectionTest.class.getResourceAsStream(
        "/com/here/xyz/test/featureWithNumberId.json")) {
      XyzFeatureCollection fc = JsonSerializable.deserialize(is);
      assertEquals(1, fc.getFeatures().size());
    }
  }

  @Test
  public void testDeserializeLarger() throws IOException {
    try (final InputStream is =
        LazyParsedFeatureCollectionTest.class.getResourceAsStream("/com/here/xyz/test/processedData.json")) {
      XyzFeatureCollection fc = JsonSerializable.deserialize(is);
      assertEquals(252, fc.getFeatures().size());
    }
  }

  @Test
  public void testDeserializeWithNullFeature() throws IOException {
    try (final InputStream is =
        LazyParsedFeatureCollectionTest.class.getResourceAsStream("/com/here/xyz/test/nullFeature.json")) {
      XyzFeatureCollection fc = JsonSerializable.deserialize(is);
      assertEquals(1, fc.getFeatures().size());
      assertNull(fc.getFeatures().get(0));
    }
  }

  @Test
  public void testDeserializeWithFeatureMissingType() throws IOException {
    try (final InputStream is = LazyParsedFeatureCollectionTest.class.getResourceAsStream(
        "/com/here/xyz/test/featureMissingType.json")) {
      final String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      XyzFeatureCollection fc = JsonSerializable.deserialize(input);
      assertEquals(1, fc.getFeatures().size());
      final XyzFeature feature = fc.getFeatures().get(0);
      assertEquals("1234", feature.getId());
    }
  }
}
