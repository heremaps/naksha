package com.here.naksha.lib.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.Member;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import org.junit.jupiter.api.Test;

class CollectionIndexPolicyTest {

  @Test
  void normalizeForHubCreationDefaultsNullIndicesOnADeepCopy() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog");

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNotSame(source, normalized);
    assertEquals("source_collection", source.getId());
    assertEquals("source_catalog", source.getCatalogId());
    assertNull(source.getIndices());
    assertEquals("target_collection", normalized.getId());
    assertEquals("target_catalog", normalized.getCatalogId());
    assertIndexNames(normalized, "tags", "geo", "next_version");
  }

  @Test
  void normalizeForHubCreationPreservesExplicitIndicesOnADeepCopy() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withIndices(new Index("custom", "id"));

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNotSame(source, normalized);
    assertNotSame(source.getIndices(), normalized.getIndices());
    assertNotSame(source.getIndices().get(0), normalized.getIndices().get(0));
    assertIndexNames(source, "custom");
    assertIndexNames(normalized, "custom");
  }

  @Test
  void normalizeForHubCreationPreservesExplicitEmptyIndices() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withIndices(new IndexList());

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNotNull(normalized.getIndices());
    assertEquals(0, normalized.getIndices().size());
    assertEquals(0, source.getIndices().size());
  }

  @Test
  void normalizeForHubCreationDoesNotAddXyzIndicesToCustomMembers() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withMembers(new Member("score", MemberType.INT64, null));

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNull(source.getIndices());
    assertNull(normalized.getIndices());
    assertNotSame(source.getMembers(), normalized.getMembers());
    assertEquals("score", normalized.getMembers().get(0).getName());
  }

  @Test
  void hubSlimCollectionCreatesStructuredDefaults() {
    final NakshaCollection collection =
        CollectionIndexPolicy.hubSlimCollection("target_collection", "target_catalog");

    assertEquals("target_collection", collection.getId());
    assertEquals("target_catalog", collection.getCatalogId());
    assertIndexNames(collection, "tags", "geo", "next_version");
  }

  private static void assertIndexNames(
      final NakshaCollection collection,
      final String... expectedNames) {
    final IndexList indices = collection.getIndices();
    assertNotNull(indices);
    assertEquals(expectedNames.length, indices.size());
    for (int i = 0; i < expectedNames.length; i++) {
      assertEquals(expectedNames[i], indices.get(i).getName());
    }
  }
}
