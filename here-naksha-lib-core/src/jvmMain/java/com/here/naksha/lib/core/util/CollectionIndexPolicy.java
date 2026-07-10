package com.here.naksha.lib.core.util;

import naksha.base.StringList;
import naksha.model.objects.NakshaCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CollectionIndexPolicy {

  private CollectionIndexPolicy() {}

  public static @NotNull StringList hubSlimIndices() {
    return StringList.of("id", "tags", "gist_geo", "next_version");
  }

  public static @NotNull NakshaCollection hubSlimCollection(
      final @NotNull String collectionId,
      final @NotNull String mapId) {
    return normalizeForHubCreation(null, collectionId, mapId);
  }

  public static @NotNull NakshaCollection normalizeForHubCreation(
      final @Nullable NakshaCollection collection,
      final @NotNull String collectionId,
      final @NotNull String mapId) {
    final NakshaCollection normalized = collection == null ? new NakshaCollection() : collection.copy(true);
    normalized.setId(collectionId);
    normalized.setMapId(mapId);
    if (normalized.getIndices() == null) {
      normalized.setIndices(hubSlimIndices());
    }
    return normalized;
  }
}
