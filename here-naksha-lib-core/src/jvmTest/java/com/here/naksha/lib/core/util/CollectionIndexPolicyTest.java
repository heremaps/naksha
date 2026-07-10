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
