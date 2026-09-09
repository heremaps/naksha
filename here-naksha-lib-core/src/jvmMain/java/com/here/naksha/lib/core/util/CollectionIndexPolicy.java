package com.here.naksha.lib.core.util;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardIndices;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzIndices;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  public static @NotNull IndexList hubSlimIndices() {
    return IndexList.of(
        XyzIndices.XyzTags,
        StandardIndices.Geometry,
        new Index("fn_nv", StandardMembers.FeatureNumber.getName(), StandardMembers.NextVersion.getName()));
  }

  public static @NotNull NakshaCollection hubSlimCollection(
      final @NotNull String collectionId,
      final @NotNull String catalogId) {
    return normalizeForHubCreation(null, collectionId, catalogId);
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @Nullable NakshaCollection collection,
      final @NotNull String collectionId,
      final @NotNull String catalogId) {
    final NakshaCollection normalized =
        collection == null ? new NakshaCollection() : collection.copy(true);
    normalized.setId(collectionId);
    normalized.setCatalogId(catalogId);
    if (normalized.getIndices() == null) {
      normalized.setIndices(normalized.getMembers() == null ? hubSlimIndices() : new IndexList());
    }
    return normalized;
  }
}
