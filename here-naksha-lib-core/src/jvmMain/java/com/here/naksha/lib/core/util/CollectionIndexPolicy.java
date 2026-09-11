package com.here.naksha.lib.core.util;

import naksha.model.objects.Index;
import naksha.model.objects.IndexList;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.StandardIndices;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzIndices;
import org.jetbrains.annotations.NotNull;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  public static @NotNull IndexList hubSlimIndices() {
    return IndexList.of(
        XyzIndices.XyzTags,
        StandardIndices.Geometry,
        new Index("fn_nv", StandardMembers.FeatureNumber.getName(), StandardMembers.NextVersion.getName()));
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @NotNull String collectionId,
      final @NotNull String catalogId) {
    final NakshaCollection collection = new NakshaCollection();
    collection.setId(collectionId);
    collection.setCatalogId(catalogId);
    return normalizeForHubCreation(collection);
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @NotNull NakshaCollection collection) {
    if (collection.getIndices() == null) {
      collection.setIndices(hubSlimIndices());
    }
    return collection;
  }
}
