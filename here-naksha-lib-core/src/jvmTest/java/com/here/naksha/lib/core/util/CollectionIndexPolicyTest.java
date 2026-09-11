package com.here.naksha.lib.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.Member;
import naksha.model.objects.MemberList;
import naksha.model.objects.MemberType;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardIndices;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzIndices;
import naksha.model.objects.XyzMembers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CollectionIndexPolicyTest {

  @Test
  void hubSlimIndicesReturnsFreshStructuredDefaultList() {
    final IndexList first = CollectionIndexPolicy.hubSlimIndices();
    final IndexList second = CollectionIndexPolicy.hubSlimIndices();

    assertNotSame(first, second);
    assertEquals(3, first.size());
    assertEquals("tags", first.get(0).getName());
    assertEquals(XyzMembers.XyzTags.getName(), first.get(0).getOn().get(0));
    assertEquals("geo", first.get(1).getName());
    assertEquals(StandardMembers.Geometry.getName(), first.get(1).getOn().get(0));
    final Index fnNv = first.get(2);
    assertEquals("fn_nv", fnNv.getName());
    assertEquals(2, fnNv.getOn().size());
    assertEquals(StandardMembers.FeatureNumber.getName(), fnNv.getOn().get(0));
    assertEquals(StandardMembers.NextVersion.getName(), fnNv.getOn().get(1));
    assertNull(fnNv.getInclude());
    assertFalse(fnNv.isUnique());
    assertFalse(fnNv.isConditional());
    assertSame(XyzIndices.XyzTags, first.get(0));
    assertSame(StandardIndices.Geometry, first.get(1));
    assertNotSame(first.get(2), second.get(2));
  }

  @Test
  void normalizeForHubCreationDefaultsNullMembersAndIndicesInPlace() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog");

    final NakshaCollection normalized = CollectionIndexPolicy.normalizeForHubCreation(source);

    assertSame(source, normalized);
    assertEquals("source_collection", source.getId());
    assertEquals("source_catalog", source.getCatalogId());
    assertNull(source.getDatabaseId());
    assertNull(source.getMembers());
    assertIndexNames(source, "tags", "geo", "fn_nv");
  }

  @Test
  void normalizeForHubCreationPreservesExplicitEmptyIndicesInPlace() {
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withIndices(new IndexList());
    final IndexList indices = source.getIndices();

    final NakshaCollection normalized = CollectionIndexPolicy.normalizeForHubCreation(source);

    assertSame(source, normalized);
    assertSame(indices, normalized.getIndices());
    assertEquals(0, normalized.getIndices().size());
  }

  @Test
  void normalizeForHubCreationPreservesCustomIndicesInPlace() {
    final Index customIndex = new Index("custom", "score");
    final NakshaCollection source = new NakshaCollection("source_collection", "source_catalog")
        .withMembers(new Member("score", MemberType.INT64, null))
        .withIndices(customIndex);
    final IndexList indices = source.getIndices();
    final Index storedCustomIndex = source.getIndices().get(0);

    final NakshaCollection normalized = CollectionIndexPolicy.normalizeForHubCreation(source);

    assertSame(source, normalized);
    assertSame(indices, normalized.getIndices());
    assertSame(storedCustomIndex, normalized.getIndices().get(0));
    assertIndexNames(normalized, "custom");
    assertEquals("score", normalized.getIndices().get(0).getOn().get(0));
  }

  @Test
  void normalizeForHubCreationBuildsNewCollectionFromIds() {
    final NakshaCollection first = CollectionIndexPolicy.normalizeForHubCreation(
        "target_collection", "target_catalog");
    final NakshaCollection second = CollectionIndexPolicy.normalizeForHubCreation(
        "target_collection", "target_catalog");

    assertEquals("target_collection", first.getId());
    assertEquals("target_catalog", first.getCatalogId());
    assertNull(first.getDatabaseId());
    assertNull(first.getMembers());
    assertIndexNames(first, "tags", "geo", "fn_nv");
    assertNotSame(first, second);
    assertNotSame(first.getIndices(), second.getIndices());
  }

  @Test
  void normalizeForHubCreationPreservesDefaultsOnRepeatedCalls() {
    final NakshaCollection collection = CollectionIndexPolicy.normalizeForHubCreation(
        "target_collection", "target_catalog");
    final IndexList indices = collection.getIndices();

    final NakshaCollection normalized = CollectionIndexPolicy.normalizeForHubCreation(collection);

    assertSame(collection, normalized);
    assertSame(indices, normalized.getIndices());
    assertIndexNames(normalized, "tags", "geo", "fn_nv");
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
