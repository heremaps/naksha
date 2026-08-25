package com.here.naksha.lib.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.Member;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardIndices;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzIndices;
import naksha.model.objects.XyzMembers;
import org.junit.jupiter.api.Test;

class CollectionIndexPolicyTest {

  @Test
  void hubSlimIndicesReturnsFreshStructuredDefaultList() {
    final IndexList first = CollectionIndexPolicy.hubSlimIndices();
    final IndexList second = CollectionIndexPolicy.hubSlimIndices();

    assertNotSame(first, second);
    assertEquals(2, first.size());
    assertEquals("tags", first.get(0).getName());
    assertEquals(XyzMembers.XyzTags.getName(), first.get(0).getOn().get(0));
    assertEquals("geo", first.get(1).getName());
    assertEquals(StandardMembers.Geometry.getName(), first.get(1).getOn().get(0));
    assertSame(XyzIndices.XyzTags, first.get(0));
    assertSame(StandardIndices.Geometry, first.get(1));
  }

  @Test
  void normalizeForHubCreationDefaultsNullMembersAndIndicesOnADeepCopy() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog");

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNotSame(source, normalized);
    assertEquals("source_collection", source.getId());
    assertEquals("source_catalog", source.getCatalogId());
    assertNull(source.getMembers());
    assertNull(source.getIndices());
    assertEquals("target_collection", normalized.getId());
    assertEquals("target_catalog", normalized.getCatalogId());
    assertIndexNames(normalized, "tags", "geo");
  }

  @Test
  void normalizeForHubCreationPreservesExplicitEmptyIndicesOnADeepCopy() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withIndices(new IndexList());

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNotSame(source, normalized);
    assertNotSame(source.getIndices(), normalized.getIndices());
    assertEquals(0, source.getIndices().size());
    assertEquals(0, normalized.getIndices().size());
  }

  @Test
  void normalizeForHubCreationPreservesCustomIndicesOnADeepCopy() {
    final Index customIndex = new Index("custom", "score");
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withIndices(customIndex);

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNotSame(source, normalized);
    assertNotSame(source.getIndices(), normalized.getIndices());
    assertNotSame(source.getIndices().get(0), normalized.getIndices().get(0));
    assertIndexNames(source, "custom");
    assertIndexNames(normalized, "custom");
    assertEquals("score", normalized.getIndices().get(0).getOn().get(0));
  }

  @Test
  void normalizeForHubCreationUsesEmptyIndicesForExplicitMembers() {
    final Member customMember = new Member("score", MemberType.INT64, null);
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withMembers(customMember);

    final NakshaCollection normalized =
        CollectionIndexPolicy.normalizeForHubCreation(source, "target_collection", "target_catalog");

    assertNull(source.getIndices());
    assertNotSame(source.getMembers(), normalized.getMembers());
    assertEquals("score", source.getMembers().get(0).getName());
    assertEquals("score", normalized.getMembers().get(0).getName());
    assertEquals(0, normalized.getIndices().size());
  }

  @Test
  void hubSlimCollectionDelegatesToHubCreationNormalization() {
    final NakshaCollection collection =
        CollectionIndexPolicy.hubSlimCollection("target_collection", "target_catalog");

    assertEquals("target_collection", collection.getId());
    assertEquals("target_catalog", collection.getCatalogId());
    assertIndexNames(collection, "tags", "geo");
  }

  private static void assertIndexNames(
      final NakshaCollection collection,
      final String... expectedNames) {
    final IndexList indices = collection.getIndices();
    assertEquals(expectedNames.length, indices.size());
    for (int i = 0; i < expectedNames.length; i++) {
      assertEquals(expectedNames[i], indices.get(i).getName());
    }
  }
}
