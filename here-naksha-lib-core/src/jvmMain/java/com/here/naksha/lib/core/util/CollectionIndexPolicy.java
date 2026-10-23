package com.here.naksha.lib.core.util;

import naksha.model.objects.IndexList;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.XyzIndices;
import org.jetbrains.annotations.NotNull;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  /**
   * The indices of collections created by the Hub without explicit indices, see {@link XyzIndices#HUB_SLIM_INDICES}.
   */
  public static @NotNull IndexList hubSlimIndices() {
    return new IndexList(XyzIndices.HUB_SLIM_INDICES);
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
