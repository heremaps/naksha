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
package com.here.naksha.lib.core.util.diff;

import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.json.JsonObject;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"rawtypes", "ConstantConditions"})
class PatcherTest {

  @Test
  void basic() throws IOException {
    final XyzFeature f1 =
        JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_1.json"), XyzFeature.class);
    assertNotNull(f1);

    final XyzFeature f2 =
        JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_2.json"), XyzFeature.class);
    assertNotNull(f2);

    final Difference diff = Patcher.getDifference(f1, f2);
    assertNotNull(diff);

    final XyzFeature f1_patched_to_f2 = Patcher.patch(f1, diff);
    assertNotNull(f1_patched_to_f2);

    final Difference newDiff = Patcher.getDifference(f1_patched_to_f2, f2);
    assertNull(newDiff);
  }

  @Test
  void testBasicNestedJson() {
    final JsonObject f3 =
            JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_3.json"), JsonObject.class);
    assertNotNull(f3);
    final JsonObject f4 =
            JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_4.json"), JsonObject.class);
    assertNotNull(f4);

    final Difference diff34 = Patcher.getDifference(f3, f4);
    assertNotNull(diff34);
    assert (diff34 instanceof MapDiff);
    final MapDiff mapDiff34 = (MapDiff) diff34;
    assertTrue(mapDiff34.get("isAdded") instanceof InsertOp);
    assertTrue(mapDiff34.get("map") instanceof MapDiff);
    assertTrue(mapDiff34.get("array") instanceof ListDiff);
    final MapDiff nestedMapDiff34 = (MapDiff) mapDiff34.get("map");
    assertTrue(nestedMapDiff34.get("willBeUpdated") instanceof UpdateOp);
    assertTrue(nestedMapDiff34.get("willBeDeleted") instanceof RemoveOp);
    final ListDiff nestedArrayDiff34 = (ListDiff) mapDiff34.get("array");
    assertTrue(nestedArrayDiff34.get(0) instanceof MapDiff);
    assertTrue(((MapDiff) nestedArrayDiff34.get(0)).get("isAddedProperty") instanceof InsertOp);
    assertTrue(((MapDiff) nestedArrayDiff34.get(0)).get("willBeDeletedProperty") instanceof RemoveOp);
    assertTrue(nestedArrayDiff34.get(1) instanceof RemoveOp);

    final JsonObject f5 =
            JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_5.json"), JsonObject.class);
    assertNotNull(f5);
    final Difference diff35 = Patcher.getDifference(f3, f5);
    assertNotNull(diff35);
    assert (diff35 instanceof MapDiff);
    final MapDiff mapDiff35 = (MapDiff) diff35;
    assertEquals(1,mapDiff35.size());
    assertTrue(mapDiff35.get("array") instanceof ListDiff);
    final ListDiff nestedArrayDiff35 = (ListDiff) mapDiff35.get("array");
    // The patcher compares array element by element in order,
    // so the nested JSON in feature 3 is compared against the string in feature 5
    // and the string in feature 3 is against the nested JSON in feature 5
    assertTrue(nestedArrayDiff35.get(0) instanceof UpdateOp);
    assertTrue(nestedArrayDiff35.get(1) instanceof UpdateOp);
  }

  private static boolean ignoreAll(@NotNull Object key, @Nullable Map source, @Nullable Map target) {
    return true;
  }

  @Test
  void testIgnoreAll() throws IOException {
    final XyzFeature f1 =
        JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_1.json"), XyzFeature.class);
    assertNotNull(f1);

    final XyzFeature f2 =
        JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_2.json"), XyzFeature.class);
    assertNotNull(f2);

    final Difference diff = Patcher.getDifference(f1, f2, PatcherTest::ignoreAll);
    assertNull(diff);
  }

  private static boolean ignoreXyzProps(@NotNull Object key, @Nullable Map source, @Nullable Map target) {
    if (source instanceof XyzNamespace || target instanceof XyzNamespace) {
      return "txn".equals(key)
          || "txn_next".equals(key)
          || "txn_uuid".equals(key)
          || "uuid".equals(key)
          || "puuid".equals(key)
          || "version".equals(key)
          || "rt_ts".equals(key)
          || "createdAt".equals(key)
          || "updatedAt".equals(key);
    }
    return false;
  }

  @Test
  void testXyzNamespace() throws IOException {
    final XyzFeature f1 =
        JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_1.json"), XyzFeature.class);
    assertNotNull(f1);

    final XyzFeature f2 =
        JsonSerializable.deserialize(IoHelp.readResource("patcher/feature_2.json"), XyzFeature.class);
    assertNotNull(f2);

    final Difference rawDiff = Patcher.getDifference(f1, f2, PatcherTest::ignoreXyzProps);

    final MapDiff feature = assertInstanceOf(MapDiff.class, rawDiff);
    assertEquals(1, feature.size());
    final MapDiff properties = assertInstanceOf(MapDiff.class, feature.get("properties"));
    assertEquals(1, properties.size());
    final MapDiff xyzNs = assertInstanceOf(MapDiff.class, properties.get("@ns:com:here:xyz"));
    assertEquals(2, xyzNs.size());
    final UpdateOp action = assertInstanceOf(UpdateOp.class, xyzNs.get("action"));
    assertEquals(EXyzAction.CREATE, action.oldValue());
    assertEquals(EXyzAction.UPDATE, action.newValue());
    final ListDiff tags = assertInstanceOf(ListDiff.class, xyzNs.get("tags"));
    assertEquals(23, tags.size());
    for (int i = 0; i < 22; i++) {
      assertNull(tags.get(i));
    }
    final InsertOp inserted = assertInstanceOf(InsertOp.class, tags.get(22));
    assertEquals("utm_dummy_update", inserted.newValue());
    assertNull(inserted.oldValue());
  }
}
