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
package com.here.naksha.lib.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import naksha.base.StringList;
import naksha.model.objects.NakshaCollection;
import org.junit.jupiter.api.Test;

class CollectionIndexPolicyTest {

  @Test
  void normalizeForHubCreationDefaultsNullIndicesOnACopy() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_map");
    source.setIndices(null);

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_map");

    assertNotSame(source, normalized);
    assertEquals("source_collection", source.getId());
    assertEquals("source_map", source.getMapId());
    assertNull(source.getIndices());
    assertEquals("target_collection", normalized.getId());
    assertEquals("target_map", normalized.getMapId());
    assertIndices(normalized, "id", "tags", "gist_geo", "next_version");
  }

  @Test
  void normalizeForHubCreationPreservesExplicitIndicesOnACopy() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_map");
    source.setIndices(StringList.of("id", "tags"));

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_map");

    assertNotSame(source, normalized);
    assertNotSame(source.getIndices(), normalized.getIndices());
    assertEquals("source_collection", source.getId());
    assertEquals("source_map", source.getMapId());
    assertIndices(source, "id", "tags");
    assertEquals("target_collection", normalized.getId());
    assertEquals("target_map", normalized.getMapId());
    assertIndices(normalized, "id", "tags");
  }

  @Test
  void hubSlimCollectionCreatesDefaults() {
    final NakshaCollection normalized = CollectionIndexPolicy.hubSlimCollection("target_collection", "target_map");

    assertEquals("target_collection", normalized.getId());
    assertEquals("target_map", normalized.getMapId());
    assertIndices(normalized, "id", "tags", "gist_geo", "next_version");
  }

  private static void assertIndices(final NakshaCollection collection, final String... expectedIndices) {
    final StringList indices = collection.getIndices();
    assertNotNull(indices);
    assertTrue(indices.containsStringsInOrder(expectedIndices));
  }
}
